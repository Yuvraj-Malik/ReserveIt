package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.engine.Slot;
import com.resrvit.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Who may decide on a request, and the decision itself: claims for shared admin queues,
 * strict queue order, item assignment, lab-assistant fallback and emergency powers,
 * bumping with alternative offers, manual reorder and reassignment.
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    public enum Mode { NONE, NORMAL, FALLBACK, EMERGENCY }

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("EEE d MMM");

    private final BookingRepository bookings;
    private final SlotOfferRepository offers;
    private final QueueAuditRepository queueAudit;
    private final UserRepository users;
    private final AvailabilityService availability;
    private final NotificationService notifications;
    private final AuditService audit;
    private final SettingsService settings;
    private final TimeService time;
    private final QueueEvents queueEvents;
    private final CalendarService calendar;

    // ------------------------------------------------------------------ permissions

    /** How (if at all) this user may approve or reject this booking right now. */
    public Mode modeFor(User u, Booking b) {
        if (u == null || !u.isActive()) return Mode.NONE;
        if (u.getRole() == Role.SUPER_ADMIN) return Mode.NORMAL;
        return switch (b.getKind()) {
            case EQUIPMENT -> {
                User inCharge = b.getLab().getInCharge();
                if (u.getRole() == Role.FACULTY && inCharge != null && inCharge.sameAs(u)) yield Mode.NORMAL;
                if (u.getRole() == Role.LAB_ASSISTANT && u.getLab() != null && u.getLab().sameAs(b.getLab())) {
                    yield fallbackAllowed(b) ? Mode.FALLBACK : Mode.EMERGENCY;
                }
                yield Mode.NONE;
            }
            case LAB_ROOM -> b.getRoom().getApprover() != null && b.getRoom().getApprover().sameAs(u) ? Mode.NORMAL : Mode.NONE;
            case GD_ROOM -> u.getRole() == Role.GD_ADMIN ? Mode.NORMAL : Mode.NONE;
            case SOCIETY_ROOM -> u.getRole() == Role.SOCIETY_ADMIN ? Mode.NORMAL : Mode.NONE;
        };
    }

    /**
     * Lab assistant fallback: the in-charge is on leave (or missing), or an urgent/evaluation
     * request has waited longer than the fallback window (2 hours by default).
     */
    public boolean fallbackAllowed(Booking b) {
        User inCharge = b.getLab().getInCharge();
        if (inCharge == null || inCharge.isOnLeave() || !inCharge.isActive()) return true;
        boolean priority = b.isUrgent() || b.getReason() == BookingReason.EVALUATION_PRACTICE;
        int hours = settings.getInt(SettingsService.FALLBACK_HOURS, 2);
        return priority && b.getCreatedAt().plusHours(hours).isBefore(time.now());
    }

    /** GD and society admins share one queue per category; acting requires holding the claim. */
    public static boolean needsClaim(Booking b) {
        return b.getKind() == BookingKind.GD_ROOM || b.getKind() == BookingKind.SOCIETY_ROOM;
    }

    // ------------------------------------------------------------------ claims

    @Transactional
    public void claim(Booking b, User admin) {
        if (!b.isPending()) throw new BookingException("This request is no longer pending.");
        if (modeFor(admin, b) == Mode.NONE) throw new BookingException("You can't act on this request.");
        if (b.getClaimedBy() != null && !b.getClaimedBy().sameAs(admin)) {
            throw new BookingException("Already claimed by " + b.getClaimedBy().getFullName() + ".");
        }
        b.setClaimedBy(admin);
        b.setClaimedAt(time.now());
        try {
            bookings.saveAndFlush(b);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BookingException("Someone else just acted on this request. Refresh the queue.");
        }
        audit.log(b, admin, ApprovalAction.CLAIMED, null);
    }

    @Transactional
    public void release(Booking b, User admin) {
        if (b.getClaimedBy() == null || (!b.getClaimedBy().sameAs(admin) && admin.getRole() != Role.SUPER_ADMIN)) {
            throw new BookingException("You don't hold this claim.");
        }
        b.setClaimedBy(null);
        b.setClaimedAt(null);
        bookings.save(b);
        audit.log(b, admin, ApprovalAction.RELEASED, null);
    }

    private void ensureClaim(Booking b, User actor) {
        if (!needsClaim(b)) return;
        if (b.getClaimedBy() == null) {
            claim(b, actor);
        } else if (!b.getClaimedBy().sameAs(actor) && actor.getRole() != Role.SUPER_ADMIN) {
            throw new BookingException("Claimed by " + b.getClaimedBy().getFullName() + "; only they can decide it.");
        }
    }

    // ------------------------------------------------------------------ queue order

    /** Pending requests competing for the same resource and slot, first in line first. */
    public List<Booking> queueOf(Booking b) {
        List<Booking> pending = switch (b.getKind()) {
            case EQUIPMENT -> bookings.equipmentOnDay(b.getLab(), b.getEquipmentType(), b.getSlotDate(), List.of(BookingStatus.PENDING));
            case GD_ROOM, LAB_ROOM -> bookings.roomOnDay(b.getRoom(), b.getSlotDate(), List.of(BookingStatus.PENDING)).stream()
                    .filter(o -> o.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(o.getEndTime())).toList();
            case SOCIETY_ROOM -> bookings.societyOverlapping(b.getRoom(), b.getSlotDate(), b.getEndDate(), List.of(BookingStatus.PENDING));
        };
        List<Booking> sorted = new ArrayList<>(pending);
        sorted.sort(Comparator.comparingLong(Booking::getQueueRank));
        return sorted;
    }

    public Optional<Booking> aheadOf(Booking b) {
        for (Booking o : queueOf(b)) {
            if (o.getId().equals(b.getId())) return Optional.empty();
            if (!hasOpenOffers(o)) return Optional.of(o);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ decisions

    @Transactional(noRollbackFor = NoCapacityException.class)
    public Booking approve(Booking b, User actor, String note) {
        Mode mode = modeFor(actor, b);
        if (mode == Mode.NONE) throw new BookingException("You can't approve this request.");
        if (mode == Mode.EMERGENCY) {
            throw new BookingException("The in-charge is available and the 2-hour fallback hasn't passed. Use emergency approval with a reason if this can't wait.");
        }
        if (!b.isPending()) throw new BookingException("This request is no longer pending.");
        ensureClaim(b, actor);
        Optional<Booking> ahead = aheadOf(b);
        if (ahead.isPresent()) {
            throw new BookingException("Approvals follow queue order: request #" + ahead.get().getId() + " from "
                    + ahead.get().getForLabel() + " is ahead. Decide that one first.");
        }
        doApprove(b, actor, note, mode == Mode.FALLBACK ? ApprovalAction.FALLBACK_APPROVED : ApprovalAction.APPROVED);
        if (mode == Mode.FALLBACK) {
            notifications.notify(b.getLab().getInCharge(), actor.getFullName() + " approved request #" + b.getId()
                    + " (" + b.getResourceLabel() + ", " + b.getWhenLabel() + ") under the lab-assistant fallback.", "/bookings/" + b.getId());
        }
        return b;
    }

    /** Lab assistant emergency approval: skips the fallback wait and queue order, needs a reason, always audited. */
    @Transactional(noRollbackFor = NoCapacityException.class)
    public Booking emergencyApprove(Booking b, User assistant, String reason) {
        if (assistant.getRole() != Role.LAB_ASSISTANT || modeFor(assistant, b) == Mode.NONE) {
            throw new BookingException("Only this lab's assistant can use emergency approval.");
        }
        if (reason == null || reason.isBlank()) throw new BookingException("Emergency approval needs a reason.");
        if (!b.isPending()) throw new BookingException("This request is no longer pending.");
        doApprove(b, assistant, reason, ApprovalAction.EMERGENCY_APPROVED);
        b.setEmergency(true);
        bookings.save(b);
        notifications.notify(b.getLab().getInCharge(), "Emergency approval by " + assistant.getFullName() + " for request #" + b.getId()
                + " (" + b.getResourceLabel() + ", " + b.getWhenLabel() + "): " + reason, "/bookings/" + b.getId());
        return b;
    }

    /** Faculty in-charge booking their own lab's resources: approved at once, logged. */
    @Transactional
    public void directApprove(Booking b, User faculty) {
        b.setDirectBooking(true);
        doApprove(b, faculty, "Direct booking by in-charge", ApprovalAction.DIRECT_BOOKING);
    }

    private void doApprove(Booking b, User actor, String note, ApprovalAction action) {
        switch (b.getKind()) {
            case EQUIPMENT -> {
                if (calendar.isHoliday(b.getSlotDate())) {
                    throw new BookingException("Labs are closed on " + D.format(b.getSlotDate()) + ". Offer the student other days instead.");
                }
                Optional<Equipment> item = availability.freeItem(b.getLab(), b.getEquipmentType(), b.getSlotDate(), b.getId());
                if (item.isEmpty()) {
                    bumpQueue(b.getLab(), b.getEquipmentType(), b.getSlotDate());
                    throw new NoCapacityException("No " + b.getEquipmentType().getName() + " is free on " + D.format(b.getSlotDate())
                            + ". Waiting requests have been offered other slots.");
                }
                b.setEquipment(item.get());
                b.setQrToken(UUID.randomUUID().toString().replace("-", ""));
            }
            case GD_ROOM, LAB_ROOM -> {
                if (!availability.isRoomFree(b.getRoom(), b.getSlotDate(), b.getStartTime(), b.getEndTime(), b.getId())) {
                    throw new BookingException("That slot is no longer free.");
                }
            }
            case SOCIETY_ROOM -> {
                if (!bookings.societyOverlapping(b.getRoom(), b.getSlotDate(), b.getEndDate(), Booking.OCCUPYING).isEmpty()) {
                    throw new BookingException("Another society now holds part of that range.");
                }
            }
        }
        b.setStatus(BookingStatus.APPROVED);
        b.setDecidedBy(actor);
        b.setDecidedAt(time.now());
        b.setDecisionNote(note);
        b.setWaitlistPriority(false);
        bookings.save(b);
        voidOffers(b);
        audit.log(b, actor, action, note);
        notifications.notifyBookingParties(b, "Approved: " + b.getResourceLabel() + ", " + b.getWhenLabel()
                + (b.isForEquipment() ? ". Show the QR code at the lab to collect it." : "."));
        afterApproval(b);
    }

    /** Once a slot is full, everyone still waiting for it is bumped with alternatives. */
    private void afterApproval(Booking b) {
        switch (b.getKind()) {
            case EQUIPMENT -> {
                if (availability.free(b.getLab(), b.getEquipmentType(), b.getSlotDate()) == 0) {
                    bumpQueue(b.getLab(), b.getEquipmentType(), b.getSlotDate());
                }
                queueEvents.labChanged(b.getLab());
            }
            case GD_ROOM, LAB_ROOM -> {
                for (Booking o : bookings.roomOnDay(b.getRoom(), b.getSlotDate(), List.of(BookingStatus.PENDING))) {
                    if (o.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(o.getEndTime())) {
                        rejectAsTaken(o);
                    }
                }
            }
            case SOCIETY_ROOM -> {
                for (Booking o : bookings.societyOverlapping(b.getRoom(), b.getSlotDate(), b.getEndDate(), List.of(BookingStatus.PENDING))) {
                    o.setStatus(BookingStatus.REJECTED);
                    o.setDecisionNote("Another society was approved for overlapping dates.");
                    o.setDecidedAt(time.now());
                    bookings.save(o);
                    audit.log(o, null, ApprovalAction.REJECTED, o.getDecisionNote());
                    notifications.notifyBookingParties(o, "Not available: " + o.getResourceLabel() + ", " + o.getWhenLabel() + ". " + o.getDecisionNote());
                }
            }
        }
    }

    private void rejectAsTaken(Booking o) {
        List<Slot> alt = availability.nearestRoomSlots(o.getRoom(), o.getSlotDate(), o.getStartTime(), o.getEndTime(), 3, 3, o.getId());
        DateTimeFormatter t = DateTimeFormatter.ofPattern("h:mm a");
        String suggestions = alt.isEmpty() ? "No nearby slot is free."
                : "Free nearby: " + String.join("; ", alt.stream().map(x -> D.format(x.date()) + " " + t.format(x.start()) + "–" + t.format(x.end())).toList());
        o.setStatus(BookingStatus.REJECTED);
        o.setDecisionNote("Slot went to an earlier request. " + suggestions);
        o.setDecidedAt(time.now());
        bookings.save(o);
        audit.log(o, null, ApprovalAction.REJECTED, o.getDecisionNote());
        notifications.notifyBookingParties(o, "Slot taken: " + o.getResourceLabel() + ", " + o.getWhenLabel() + ". " + suggestions);
    }

    @Transactional
    public Booking reject(Booking b, User actor, String note) {
        if (modeFor(actor, b) == Mode.NONE) throw new BookingException("You can't reject this request.");
        if (!b.isPending()) throw new BookingException("This request is no longer pending.");
        if (note == null || note.isBlank()) throw new BookingException("Give a reason for rejecting.");
        ensureClaim(b, actor);
        b.setStatus(BookingStatus.REJECTED);
        b.setDecidedBy(actor);
        b.setDecidedAt(time.now());
        b.setDecisionNote(note);
        bookings.save(b);
        voidOffers(b);
        audit.log(b, actor, ApprovalAction.REJECTED, note);
        notifications.notifyBookingParties(b, "Rejected: " + b.getResourceLabel() + ", " + b.getWhenLabel() + ". Reason: " + note);
        if (b.isForEquipment()) queueEvents.labChanged(b.getLab());
        return b;
    }

    // ------------------------------------------------------------------ bumping and offers

    /**
     * Everyone still pending for a full equipment slot gets the next free slot plus up to two
     * alternatives within 7 days. If nothing is free, they stay first in the queue.
     */
    @Transactional
    public void bumpQueue(Lab lab, EquipmentType type, LocalDate day) {
        if (availability.free(lab, type, day) > 0) return;
        for (Booking p : bookings.equipmentOnDay(lab, type, day, List.of(BookingStatus.PENDING))) {
            if (hasOpenOffers(p)) continue;
            offerAlternatives(p, null);
        }
    }

    /** Creates up to 3 alternative-day offers for a pending equipment request. Returns how many. */
    @Transactional
    public int offerAlternatives(Booking p, User actor) {
        if (!p.isForEquipment() || !p.isPending()) throw new BookingException("Alternatives can only be offered for pending equipment requests.");
        LocalDate today = time.today();
        LocalDate from = today.isAfter(p.getSlotDate().minusDays(7)) ? today : p.getSlotDate().minusDays(7);
        LocalDate to = p.getSlotDate().plusDays(7);
        if (p.getEvaluation() != null) {
            LocalDate last = p.getEvaluation().getEvalDate().minusDays(1);
            if (to.isAfter(last)) to = last;
        }
        if (time.now().toLocalTime().isAfter(CalendarService.LAB_CLOSE) && !from.isAfter(today)) from = today.plusDays(1);
        List<LocalDate> days = availability.nearestFreeDays(p.getLab(), p.getEquipmentType(), p.getSlotDate(), from, to, p.getSlotDate(), 3);
        voidOffers(p);
        if (days.isEmpty()) {
            // Stay first in line for the next item on this slot.
            long first = queueOf(p).stream().mapToLong(Booking::getQueueRank).min().orElse(p.getQueueRank());
            p.setQueueRank(Math.min(first - 1, p.getQueueRank()));
            p.setWaitlistPriority(true);
            bookings.save(p);
            audit.log(p, actor, ApprovalAction.OFFERED, "Nothing free within 7 days; kept first in queue");
            notifications.notifyBookingParties(p, "Your " + p.getEquipmentType().getName() + " slot on " + D.format(p.getSlotDate())
                    + " is full and nothing is free within 7 days. You stay first in the queue for the next available item.");
            return 0;
        }
        LocalDateTime expires = time.now().plusHours(24);
        int rank = 1;
        for (LocalDate d : days) {
            SlotOffer o = new SlotOffer();
            o.setBooking(p);
            o.setOfferedDate(d);
            o.setOptionRank(rank++);
            o.setStatus(OfferStatus.OPEN);
            o.setExpiresAt(expires);
            o.setCreatedAt(time.now());
            offers.save(o);
        }
        audit.log(p, actor, ApprovalAction.OFFERED, "Offered " + String.join(", ", days.stream().map(D::format).toList()));
        notifications.notifyBookingParties(p, "Your " + p.getEquipmentType().getName() + " slot on " + D.format(p.getSlotDate())
                + " is full. We've held " + D.format(days.get(0)) + " for you; accept it or pick another within 24 hours (if you don't respond, the first offer is kept).");
        return days.size();
    }

    public boolean hasOpenOffers(Booking b) {
        return b.getId() != null && !offers.findByBookingAndStatusOrderByOptionRankAsc(b, OfferStatus.OPEN).isEmpty();
    }

    public List<SlotOffer> openOffers(Booking b) {
        return offers.findByBookingAndStatusOrderByOptionRankAsc(b, OfferStatus.OPEN);
    }

    /** Accepting an offer moves the request to that day and confirms it if an item is still free. */
    @Transactional
    public Booking acceptOffer(SlotOffer offer, User actor, boolean automatic) {
        Booking b = offer.getBooking();
        if (offer.getStatus() != OfferStatus.OPEN || !b.isPending()) throw new BookingException("This offer is no longer open.");
        if (!automatic && !b.getRequester().sameAs(actor) && !(b.getGroup() != null && b.getGroup().getLeader().sameAs(actor))) {
            throw new BookingException("Only the requester can accept this offer.");
        }
        for (SlotOffer o : openOffers(b)) {
            o.setStatus(o.getId().equals(offer.getId()) ? (automatic ? OfferStatus.AUTO_ACCEPTED : OfferStatus.ACCEPTED) : OfferStatus.SUPERSEDED);
            offers.save(o);
        }
        LocalDate old = b.getSlotDate();
        b.setSlotDate(offer.getOfferedDate());
        bookings.save(b);
        audit.log(b, automatic ? null : actor, ApprovalAction.OFFER_ACCEPTED,
                (automatic ? "Kept automatically after 24 hours: " : "Moved from ") + D.format(old) + " to " + D.format(offer.getOfferedDate()));
        // Bumped requests are guaranteed a slot: confirm it straight away when an item is free.
        if (availability.freeItem(b.getLab(), b.getEquipmentType(), b.getSlotDate(), b.getId()).isPresent()) {
            doApprove(b, null, "Confirmed after being bumped from " + D.format(old), ApprovalAction.APPROVED);
        } else {
            offerAlternatives(b, null);
        }
        return b;
    }

    @Transactional
    public void voidOffers(Booking b) {
        for (SlotOffer o : offers.findByBookingAndStatusOrderByOptionRankAsc(b, OfferStatus.OPEN)) {
            o.setStatus(OfferStatus.SUPERSEDED);
            offers.save(o);
        }
    }

    // ------------------------------------------------------------------ reorder and reassign

    /** Lab assistant drags pending requests into a new order. Reason mandatory; every move audited. */
    @Transactional
    public void reorder(User assistant, List<Long> orderedIds, String reason) {
        if (reason == null || reason.isBlank()) throw new BookingException("A reason is required to reorder the queue.");
        if (orderedIds == null || orderedIds.size() < 2) throw new BookingException("Nothing to reorder.");
        List<Booking> list = bookings.findAllById(orderedIds);
        if (list.size() != orderedIds.size()) throw new BookingException("Some requests no longer exist. Refresh the queue.");
        Map<Long, Booking> byId = new HashMap<>();
        list.forEach(b -> byId.put(b.getId(), b));
        String key = null;
        for (Booking b : list) {
            if (!b.isPending()) throw new BookingException("Only pending requests can be reordered; approved bookings are final.");
            if (!b.isForEquipment()) throw new BookingException("Only equipment queues can be reordered.");
            if (assistant.getRole() != Role.SUPER_ADMIN && (assistant.getLab() == null || !assistant.getLab().sameAs(b.getLab()))) {
                throw new BookingException("You can only reorder your own lab's queue.");
            }
            if (key == null) key = b.getQueueKey();
            else if (!key.equals(b.getQueueKey())) throw new BookingException("All requests must be in the same queue (same item type, lab and day).");
        }
        List<Long> ranks = list.stream().map(Booking::getQueueRank).sorted().toList();
        for (int i = 0; i < orderedIds.size(); i++) {
            Booking b = byId.get(orderedIds.get(i));
            long newRank = ranks.get(i);
            if (b.getQueueRank() != newRank) {
                QueueAudit qa = new QueueAudit();
                qa.setBooking(b);
                qa.setActor(assistant);
                qa.setOldRank(b.getQueueRank());
                qa.setNewRank(newRank);
                qa.setReason(reason.trim());
                qa.setCreatedAt(time.now());
                queueAudit.save(qa);
                b.setQueueRank(newRank);
                bookings.save(b);
            }
        }
        queueEvents.labChanged(list.get(0).getLab());
    }

    /**
     * Give a freed (cancelled) slot to a waiting request: GD admins for GD slots, society admins
     * for society rooms, lab assistants for equipment. The target is moved and approved.
     */
    @Transactional
    public Booking reassign(Booking freed, Booking target, User actor) {
        if (freed.getStatus() != BookingStatus.CANCELLED) throw new BookingException("Only a cancelled slot can be reassigned.");
        if (!target.isPending()) throw new BookingException("The receiving request must be pending.");
        if (freed.getKind() != target.getKind()) throw new BookingException("Both requests must be the same kind.");
        if (modeFor(actor, target) == Mode.NONE) throw new BookingException("You can't reassign this slot.");
        String from = target.getWhenLabel();
        switch (freed.getKind()) {
            case EQUIPMENT -> {
                if (!freed.getEquipmentType().sameAs(target.getEquipmentType()) || !freed.getLab().sameAs(target.getLab())) {
                    throw new BookingException("The waiting request must be for the same item type in the same lab.");
                }
                if (target.getEvaluation() != null && !freed.getSlotDate().isBefore(target.getEvaluation().getEvalDate())) {
                    throw new BookingException("That day is not before the student's evaluation.");
                }
                target.setSlotDate(freed.getSlotDate());
            }
            case GD_ROOM, LAB_ROOM -> {
                target.setRoom(freed.getRoom());
                target.setSlotDate(freed.getSlotDate());
                target.setStartTime(freed.getStartTime());
                target.setEndTime(freed.getEndTime());
            }
            case SOCIETY_ROOM -> {
                target.setRoom(freed.getRoom());
                target.setSlotDate(freed.getSlotDate());
                target.setEndDate(freed.getEndDate());
            }
        }
        bookings.save(target);
        audit.log(target, actor, ApprovalAction.REASSIGNED, "Given the cancelled slot of #" + freed.getId() + " (was " + from + ")");
        if (needsClaim(target)) {
            target.setClaimedBy(actor);
            target.setClaimedAt(time.now());
        }
        doApprove(target, actor, "Reassigned cancelled slot #" + freed.getId(), ApprovalAction.APPROVED);
        return target;
    }

    // ------------------------------------------------------------------ helpers for views

    /** Pending requests this user can act on, grouped by what they approve. */
    public List<Booking> pendingFor(User u, List<Lab> labsInCharge, List<Room> roomsInCharge) {
        List<Booking> out = new ArrayList<>();
        if (!labsInCharge.isEmpty()) out.addAll(bookings.pendingEquipmentForLabs(labsInCharge));
        if (!roomsInCharge.isEmpty()) out.addAll(bookings.pendingRoomsFor(roomsInCharge));
        return out;
    }

    public List<User> labAssistants(Lab lab) {
        return users.findByLabAndRoleAndStatus(lab, Role.LAB_ASSISTANT, UserStatus.ACTIVE);
    }
}
