package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * QR check-out and check-in. The assistant scans the booking's QR to issue the item
 * (condition logged, due back in 24 hours) and again on return (condition, damage, lateness).
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    public static final Duration LOAN = Duration.ofHours(24);

    private final BookingRepository bookings;
    private final CheckoutRepository checkouts;
    private final EquipmentRepository equipment;
    private final IncidentRepository incidents;
    private final StrikeService strikes;
    private final NotificationService notifications;
    private final AuditService audit;
    private final TimeService time;
    private final QueueEvents queueEvents;
    private final CollisionService collisions;

    public Booking byToken(String token) {
        String t = token == null ? "" : token.trim();
        // Scanners may return the whole check-in URL; keep only the token.
        int i = t.lastIndexOf('=');
        if (i >= 0) t = t.substring(i + 1);
        i = t.lastIndexOf('/');
        if (i >= 0) t = t.substring(i + 1);
        return bookings.findByQrToken(t).orElseThrow(() -> new BookingException("No booking matches that QR code."));
    }

    @Transactional
    public Checkout issue(Booking b, User assistant, String condition) {
        requireLab(b, assistant);
        if (b.getStatus() != BookingStatus.APPROVED) throw new BookingException("Only approved bookings can be issued (this one is " + b.getStatus().getLabel().toLowerCase() + ").");
        if (b.getSlotDate().isAfter(time.today())) throw new BookingException("This booking is for " + b.getWhenLabel() + "; it can't be collected early.");
        if (b.getSlotDate().isBefore(time.today())) throw new BookingException("This booking's day has passed.");
        if (!checkouts.findByEquipmentAndReturnedAtIsNull(b.getEquipment()).isEmpty()) {
            throw new BookingException(b.getEquipment().getAssetTag() + " is still out on another loan. Assign another item from the queue screen.");
        }
        Checkout c = new Checkout();
        c.setBooking(b);
        c.setEquipment(b.getEquipment());
        c.setIssuedBy(assistant);
        c.setIssuedAt(time.now());
        c.setConditionOut(blankToNull(condition));
        c.setDueAt(time.now().plus(LOAN));
        checkouts.save(c);
        b.setStatus(BookingStatus.CHECKED_OUT);
        bookings.save(b);
        audit.log(b, assistant, ApprovalAction.CHECKED_OUT, "Condition out: " + (condition == null ? "-" : condition));
        notifications.notifyBookingParties(b, "Checked out: " + b.getResourceLabel() + ". Return it within 24 hours (by "
                + java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, h:mm a").format(c.getDueAt()) + ") to avoid a strike.");
        queueEvents.labChanged(b.getLab());
        return c;
    }

    @Transactional
    public Checkout receive(Booking b, User assistant, String condition, boolean damaged, String damageDescription, boolean sendToMaintenance) {
        requireLab(b, assistant);
        if (b.getStatus() != BookingStatus.CHECKED_OUT) throw new BookingException("This item isn't checked out.");
        Checkout c = checkouts.findFirstByBookingAndReturnedAtIsNull(b).orElseThrow(() -> new BookingException("No open checkout found."));
        if (damaged && (damageDescription == null || damageDescription.isBlank())) {
            throw new BookingException("Describe the damage for the incident report.");
        }
        c.setReturnedAt(time.now());
        c.setReceivedBy(assistant);
        c.setConditionIn(blankToNull(condition));
        c.setLate(time.now().isAfter(c.getDueAt()));
        c.setDamaged(damaged);
        checkouts.save(c);
        b.setStatus(BookingStatus.RETURNED);
        bookings.save(b);

        Equipment e = c.getEquipment();
        if (condition != null && !condition.isBlank()) e.setConditionNote(condition.trim());
        if (sendToMaintenance) e.setStatus(EquipmentStatus.MAINTENANCE);
        equipment.saveAndFlush(e);
        if (sendToMaintenance) collisions.revalidateNow(assistant);

        if (c.isLate()) {
            long hours = Duration.between(c.getDueAt(), c.getReturnedAt()).toHours();
            strikes.add(b.getRequester(), b, "Late return of " + e.getAssetTag() + " (" + hours + " h late)");
        }
        if (damaged) {
            Incident i = new Incident();
            i.setBooking(b);
            i.setEquipment(e);
            i.setLab(b.getLab());
            i.setReportedBy(assistant);
            i.setDescription(damageDescription.trim());
            i.setStatus(IncidentStatus.OPEN);
            i.setCreatedAt(time.now());
            incidents.save(i);
            notifications.notifyBookingParties(b, "Damage reported on " + e.getAssetTag() + ". You"
                    + (b.getGroup() != null ? " and your group" : "") + " can't book " + b.getLab().getName()
                    + " equipment until it's resolved.");
            notifications.notify(b.getLab().getInCharge(), "Damage reported on " + e.getAssetTag() + " by " + assistant.getFullName()
                    + ": " + damageDescription, "/assistant/incidents");
        }
        audit.log(b, assistant, ApprovalAction.RETURNED, "Returned" + (c.isLate() ? " late" : "") + (damaged ? " with damage" : "")
                + "; condition: " + (condition == null ? "-" : condition));
        if (!c.isLate() && !damaged) notifications.notifyBookingParties(b, "Returned: " + e.getAssetTag() + ". Thanks!");
        queueEvents.labChanged(b.getLab());
        return c;
    }

    @Transactional
    public void resolveIncident(Incident i, User actor, String resolution) {
        if (i.getStatus() != IncidentStatus.OPEN) throw new BookingException("Already resolved.");
        boolean allowed = actor.getRole() == Role.SUPER_ADMIN
                || (actor.getRole() == Role.LAB_ASSISTANT && actor.getLab() != null && actor.getLab().sameAs(i.getLab()))
                || (i.getLab().getInCharge() != null && i.getLab().getInCharge().sameAs(actor));
        if (!allowed) throw new BookingException("Only this lab's staff can resolve the incident.");
        if (resolution == null || resolution.isBlank()) throw new BookingException("Describe how it was resolved.");
        i.setStatus(IncidentStatus.RESOLVED);
        i.setResolvedAt(time.now());
        i.setResolvedBy(actor);
        i.setResolution(resolution.trim());
        incidents.save(i);
        if (i.getBooking() != null) {
            notifications.notifyBookingParties(i.getBooking(), "The damage report on " + (i.getEquipment() != null ? i.getEquipment().getAssetTag() : "equipment")
                    + " is resolved; you can book " + i.getLab().getName() + " equipment again.");
        }
    }

    private void requireLab(Booking b, User assistant) {
        if (!b.isForEquipment()) throw new BookingException("This QR code isn't for equipment.");
        boolean ok = assistant.getRole() == Role.SUPER_ADMIN
                || (assistant.getRole() == Role.LAB_ASSISTANT && assistant.getLab() != null && assistant.getLab().sameAs(b.getLab()));
        if (!ok) throw new BookingException("This booking belongs to " + b.getLab().getName() + ".");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
