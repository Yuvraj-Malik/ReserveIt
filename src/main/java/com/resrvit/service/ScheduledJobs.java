package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Expiry, offer timeouts and reminders. */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledJobs {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("EEE d MMM");

    private final BookingRepository bookings;
    private final SlotOfferRepository offers;
    private final CheckoutRepository checkouts;
    private final SocietyOfficeBearerRepository bearers;
    private final ApprovalService approvals;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CalendarService calendar;
    private final TimeService time;
    private final QueueEvents queueEvents;

    /** Requests nobody decided expire once their slot starts (equipment: when the pick-up window closes). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    @Transactional
    public void expirePending() {
        LocalDateTime now = time.now();
        for (Booking b : bookings.findByStatusOrderByQueueRankAsc(BookingStatus.PENDING)) {
            LocalDateTime deadline = switch (b.getKind()) {
                case EQUIPMENT -> b.getSlotDate().atTime(CalendarService.LAB_CLOSE);
                case SOCIETY_ROOM -> b.getSlotDate().atTime(calendar.societyWindow(b.getSlotDate()).start());
                default -> b.getSlotDate().atTime(b.getStartTime());
            };
            if (now.isAfter(deadline)) {
                b.setStatus(BookingStatus.EXPIRED);
                b.setDecisionNote("Nobody approved it before the slot started.");
                bookings.save(b);
                approvals.voidOffers(b);
                audit.log(b, null, ApprovalAction.EXPIRED, null);
                notifications.notifyBookingParties(b, "Expired: " + b.getResourceLabel() + ", " + b.getWhenLabel()
                        + ". Nobody with approval power acted in time.");
                if (b.isForEquipment()) queueEvents.labChanged(b.getLab());
            }
        }
    }

    /** Offers not answered within 24 hours: the first (default) offer is kept. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void settleOffers() {
        for (SlotOffer o : offers.findByStatusAndExpiresAtBefore(OfferStatus.OPEN, time.now())) {
            if (o.getStatus() != OfferStatus.OPEN || o.getOptionRank() != 1) continue;
            try {
                approvals.acceptOffer(o, null, true);
            } catch (BookingException e) {
                log.info("Offer {} could not be kept: {}", o.getId(), e.getMessage());
            }
        }
    }

    /** Morning reminders: today's bookings, overdue items, society renewals 5 days before expiry. */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void morningReminders() {
        var today = time.today();
        for (Booking b : bookings.approvedInRange(today, today)) {
            if (b.getKind() == BookingKind.SOCIETY_ROOM) continue;
            if (b.getSlotDate().equals(today)) {
                notifications.notifyBookingParties(b, "Reminder: " + b.getResourceLabel() + " today (" + b.getWhenLabel() + ").");
            }
        }
        for (Checkout c : checkouts.findByReturnedAtIsNullOrderByDueAtAsc()) {
            if (c.getDueAt().isBefore(time.now())) {
                notifications.notifyBookingParties(c.getBooking(), "Overdue: " + c.getEquipment().getAssetTag()
                        + " was due back " + D.format(c.getDueAt()) + ". Return it now; late returns are strikes.");
            }
        }
        remindRenewals();
    }

    @Transactional
    public void remindRenewals() {
        var today = time.today();
        for (Booking b : bookings.approvedInRange(today, today.plusDays(5))) {
            if (b.getKind() != BookingKind.SOCIETY_ROOM || b.isRenewalReminderSent()) continue;
            if (b.getEndDate().isAfter(today.plusDays(5))) continue;
            List<User> people = bearers.findBySociety(b.getSociety()).stream().map(SocietyOfficeBearer::getUser).toList();
            notifications.notifyAll(people, b.getSociety().getName() + "'s booking of " + b.getRoom().getName() + " ends on "
                    + D.format(b.getEndDate()) + ". Request a renewal to keep it.", "/bookings/" + b.getId());
            b.setRenewalReminderSent(true);
            bookings.save(b);
        }
    }

    /** Overdue checkouts are also nudged every two hours during the day. */
    @Scheduled(cron = "0 0 10-18/2 * * *", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void overdueNudges() {
        for (Checkout c : checkouts.findByReturnedAtIsNullOrderByDueAtAsc()) {
            if (c.getDueAt().isBefore(time.now()) && c.getDueAt().isAfter(time.now().minusHours(2))) {
                notifications.notifyBookingParties(c.getBooking(), "Just overdue: " + c.getEquipment().getAssetTag() + ". Return it to avoid a strike.");
            }
        }
    }
}
