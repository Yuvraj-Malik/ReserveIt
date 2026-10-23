package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.engine.Slot;
import com.resrvit.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Creating and cancelling requests. Every fair-use rule from the spec is enforced here. */
@Service
@RequiredArgsConstructor
public class BookingService {

    public static final int MAX_SOCIETY_DAYS = 30;
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("EEE d MMM");
    private static final AtomicLong RANK_SEQ = new AtomicLong();

    private final BookingRepository bookings;
    private final LabRepository labs;
    private final RoomRepository rooms;
    private final EquipmentTypeRepository types;
    private final EvaluationScheduleRepository evaluations;
    private final StudyGroupRepository groups;
    private final SocietyRepository societies;
    private final SocietyOfficeBearerRepository bearers;
    private final IncidentRepository incidents;
    private final UserRepository users;
    private final AvailabilityService availability;
    private final CalendarService calendar;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final AuditService audit;
    private final TimeService time;
    private final ApprovalService approvals;
    private final QueueEvents queueEvents;

    // ------------------------------------------------------------------ requests

    public record EquipmentRequest(Long labId, Long typeId, LocalDate date, BookingReason reason,
                                   String reasonText, Long evaluationId, Long groupId, boolean urgent) {}

    public record RoomRequest(Long roomId, LocalDate date, LocalTime start, LocalTime end,
                              String reasonText, Long groupId) {}

    public record SocietyRequest(Long societyId, Long roomId, LocalDate from, LocalDate to,
                                 String reasonText, Long previousBookingId) {}

    @Transactional
    public Booking requestEquipment(User user, EquipmentRequest r) {
        requireCanBook(user);
        Lab lab = labs.findById(r.labId()).orElseThrow(() -> new BookingException("Pick a lab."));
        EquipmentType type = types.findById(r.typeId()).orElseThrow(() -> new BookingException("Pick an equipment type."));
        LocalDate day = r.date();
        if (day == null) throw new BookingException("Pick a date.");
        LocalDate today = time.today();
        int lead = settings.getInt(SettingsService.LEAD_DAYS, 7);
        if (day.isBefore(today)) throw new BookingException("That date has passed.");
        if (day.equals(today) && time.now().toLocalTime().isAfter(CalendarService.LAB_CLOSE)) {
            throw new BookingException("Today's pick-up window has closed; pick a later date.");
        }
        if (day.isAfter(today.plusDays(lead))) {
            throw new BookingException("Equipment can be requested at most " + lead + " days ahead.");
        }
        if (calendar.isHoliday(day)) throw new BookingException("Labs are closed on " + D.format(day) + " (holiday).");
        if (availability.usableItems(lab, type, day).isEmpty()) {
            throw new BookingException(lab.getName() + " has no " + type.getName() + " available that day (none in stock or under maintenance).");
        }
        if (r.reason() == null) throw new BookingException("A reason is required.");
        if (r.reason() == BookingReason.OTHER && (r.reasonText() == null || r.reasonText().isBlank())) {
            throw new BookingException("Describe the reason when you pick \"Other\".");
        }

        EvaluationSchedule eval = null;
        if (r.reason() == BookingReason.EVALUATION_PRACTICE) {
            if (r.evaluationId() == null) throw new BookingException("Pick the evaluation you are practising for.");
            eval = evaluations.findById(r.evaluationId()).orElseThrow(() -> new BookingException("Unknown evaluation."));
            if (!eval.getLab().sameAs(lab)) throw new BookingException("That evaluation is in " + eval.getLab().getName() + ", not " + lab.getName() + ".");
            if (eval.getEquipmentType() != null && !eval.getEquipmentType().sameAs(type)) {
                throw new BookingException("That evaluation uses " + eval.getEquipmentType().getName() + ".");
            }
            if (!day.isBefore(eval.getEvalDate())) {
                throw new BookingException("Practice must be before the evaluation on " + D.format(eval.getEvalDate()) + ".");
            }
        }

        StudyGroup group = resolveGroup(user, r.groupId());
        requireNoOpenIncident(user, group, lab);

        boolean isStudent = user.getRole() == Role.STUDENT;
        if (isStudent || group != null) checkWeeklyLimit(user, group, type, day);

        // No duplicate live request for the same thing on the same day.
        for (Booking b : bookings.equipmentOnDay(lab, type, day, Booking.LIVE)) {
            if (b.getRequester().sameAs(user) && Objects.equals(idOf(b.getGroup()), idOf(group))) {
                throw new BookingException("You already have a request for this on " + D.format(day) + ".");
            }
        }

        Booking b = new Booking();
        b.setKind(BookingKind.EQUIPMENT);
        b.setStatus(BookingStatus.PENDING);
        b.setRequester(user);
        b.setGroup(group);
        b.setLab(lab);
        b.setEquipmentType(type);
        b.setSlotDate(day);
        b.setReason(r.reason());
        b.setReasonText(trim(r.reasonText()));
        b.setEvaluation(eval);
        b.setProtectedEval(eval != null);
        b.setUrgent(r.urgent());
        b.setQueueRank(nextRank());
        b.setCreatedAt(time.now());
        bookings.save(b);
        audit.log(b, user, ApprovalAction.REQUESTED, null);

        boolean inCharge = user.getRole() == Role.FACULTY && lab.getInCharge() != null && lab.getInCharge().sameAs(user);
        if (inCharge) {
            approvals.directApprove(b, user);
        } else {
            notifications.notify(lab.getInCharge(), "New equipment request: " + type.getName() + " on " + D.format(day)
                    + " from " + b.getForLabel(), "/faculty/approvals");
            queueEvents.labChanged(lab);
        }
        return b;
    }

    @Transactional
    public Booking requestLabRoom(User user, RoomRequest r) {
        requireCanBook(user);
        Room room = rooms.findById(r.roomId()).orElseThrow(() -> new BookingException("Pick a room."));
        if (!room.isBookable() || (room.getType() != RoomType.EVALUATION_LAB && room.getType() != RoomType.SEMINAR_ROOM)) {
            throw new BookingException(room.getName() + " can't be booked this way.");
        }
        if (room.getApprover() == null) throw new BookingException(room.getName() + " has no faculty in-charge yet, so it can't take requests.");
        validateTimes(r.date(), r.start(), r.end());
        int lead = settings.getInt(SettingsService.LEAD_DAYS, 7);
        if (r.date().isAfter(time.today().plusDays(lead))) throw new BookingException("Rooms can be requested at most " + lead + " days ahead.");
        if (r.start().isBefore(LocalTime.of(8, 0)) || r.end().isAfter(LocalTime.of(20, 0))) {
            throw new BookingException("Lab and seminar rooms can be booked between 8 AM and 8 PM.");
        }
        if (r.reasonText() == null || r.reasonText().isBlank()) throw new BookingException("Say what the room is for.");
        StudyGroup group = resolveGroup(user, r.groupId());
        if (!availability.isRoomFree(room, r.date(), r.start(), r.end(), null)) {
            throw new BookingException("That time is taken (class, maintenance or another booking). " + suggestText(room, r.date(), r.start(), r.end()));
        }

        Booking b = new Booking();
        b.setKind(BookingKind.LAB_ROOM);
        b.setStatus(BookingStatus.PENDING);
        b.setRequester(user);
        b.setGroup(group);
        b.setRoom(room);
        b.setSlotDate(r.date());
        b.setStartTime(r.start());
        b.setEndTime(r.end());
        b.setReasonText(trim(r.reasonText()));
        b.setQueueRank(nextRank());
        b.setCreatedAt(time.now());
        bookings.save(b);
        audit.log(b, user, ApprovalAction.REQUESTED, null);

        if (room.getApprover().sameAs(user)) {
            approvals.directApprove(b, user);
        } else {
            notifications.notify(room.getApprover(), "New room request: " + room.getName() + ", " + b.getWhenLabel(), "/faculty/approvals");
        }
        return b;
    }

    @Transactional
    public Booking requestGdRoom(User user, RoomRequest r) {
        requireCanBook(user);
        Room room = rooms.findById(r.roomId()).orElseThrow(() -> new BookingException("Pick a GD room."));
        if (room.getType() != RoomType.GD_ROOM || !room.isBookable()) throw new BookingException("That is not a bookable GD room.");
        if (!CalendarService.isGdSlot(r.start(), r.end())) throw new BookingException("GD rooms use fixed slots: 9–11, 11–1, 2–4 and 4–6.");
        validateTimes(r.date(), r.start(), r.end());
        int ahead = settings.getInt(SettingsService.GD_DAYS_AHEAD, 3);
        if (r.date().isAfter(time.today().plusDays(ahead))) throw new BookingException("GD rooms can be booked up to " + ahead + " days ahead.");
        StudyGroup group = resolveGroup(user, r.groupId());

        // One slot per day per student or group.
        if (!bookings.gdForUserOnDay(user, groupIds(user), r.date(), Booking.LIVE).isEmpty()) {
            throw new BookingException("You (or your group) already have a GD slot on " + D.format(r.date()) + ". One slot per day.");
        }
        if (group != null) {
            for (GroupMember m : group.getMembers()) {
                if (!bookings.gdForUserOnDay(m.getUser(), groupIds(m.getUser()), r.date(), Booking.LIVE).isEmpty()) {
                    throw new BookingException(m.getUser().getFullName() + " already has a GD slot that day.");
                }
            }
        }
        if (!availability.isRoomFree(room, r.date(), r.start(), r.end(), null)) {
            throw new BookingException("That slot is taken. " + suggestText(room, r.date(), r.start(), r.end()));
        }

        Booking b = new Booking();
        b.setKind(BookingKind.GD_ROOM);
        b.setStatus(BookingStatus.PENDING);
        b.setRequester(user);
        b.setGroup(group);
        b.setRoom(room);
        b.setSlotDate(r.date());
        b.setStartTime(r.start());
        b.setEndTime(r.end());
        b.setReasonText(trim(r.reasonText()));
        b.setQueueRank(nextRank());
        b.setCreatedAt(time.now());
        bookings.save(b);
        audit.log(b, user, ApprovalAction.REQUESTED, null);
        notifications.notifyRole(Role.GD_ADMIN, "New GD room request: " + room.getName() + ", " + b.getWhenLabel(), "/gd");
        return b;
    }

    @Transactional
    public Booking requestSocietyRoom(User user, SocietyRequest r) {
        requireCanBook(user);
        Society society = societies.findById(r.societyId()).orElseThrow(() -> new BookingException("Pick a society."));
        if (!society.isActive()) throw new BookingException(society.getName() + " is not active.");
        if (!bearers.existsBySocietyAndUser(society, user)) throw new BookingException("Only office-bearers of " + society.getName() + " can request rooms.");
        Room room = rooms.findById(r.roomId()).orElseThrow(() -> new BookingException("Pick a room."));
        if (room.getType() != RoomType.SOCIETY_ROOM || !room.isBookable()) throw new BookingException("That is not a bookable society room.");
        LocalDate from = r.from(), to = r.to();
        if (from == null || to == null) throw new BookingException("Pick a start and end date.");
        if (to.isBefore(from)) throw new BookingException("The end date is before the start date.");
        if (from.isBefore(time.today())) throw new BookingException("The start date has passed.");
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_SOCIETY_DAYS) throw new BookingException("A society booking can cover at most " + MAX_SOCIETY_DAYS + " days.");

        Booking previous = null;
        if (r.previousBookingId() != null) {
            previous = bookings.findById(r.previousBookingId()).orElseThrow(() -> new BookingException("Unknown previous booking."));
            if (!Objects.equals(idOf(previous.getSociety()), society.getId()) || !previous.getRoom().sameAs(room)) {
                throw new BookingException("A renewal must be for the same society and room.");
            }
            if (!from.isAfter(previous.getEndDate())) throw new BookingException("A renewal must start after the current booking ends (" + D.format(previous.getEndDate()) + ").");
        }
        for (Booking live : bookings.societyLive(society, room, Booking.LIVE)) {
            if (previous == null || !live.getId().equals(previous.getId())) {
                throw new BookingException(society.getName() + " already has an active or pending booking for " + room.getName() + ". One per room.");
            }
        }
        if (!bookings.societyOverlapping(room, from, to, Booking.OCCUPYING).isEmpty()) {
            throw new BookingException(room.getName() + " is already held by another society for part of that range.");
        }

        Booking b = new Booking();
        b.setKind(BookingKind.SOCIETY_ROOM);
        b.setStatus(BookingStatus.PENDING);
        b.setRequester(user);
        b.setSociety(society);
        b.setRoom(room);
        b.setSlotDate(from);
        b.setEndDate(to);
        b.setReasonText(trim(r.reasonText()));
        b.setPreviousBooking(previous);
        b.setExcludedDates(joinDates(societyExclusions(room, from, to)));
        b.setQueueRank(nextRank());
        b.setCreatedAt(time.now());
        bookings.save(b);
        audit.log(b, user, previous != null ? ApprovalAction.RENEWAL_REQUESTED : ApprovalAction.REQUESTED, null);
        notifications.notifyRole(Role.SOCIETY_ADMIN, (previous != null ? "Renewal" : "New society booking") + " request: "
                + society.getName() + ", " + room.getName() + ", " + b.getWhenLabel(), "/society-admin");
        return b;
    }

    /** Days in the range when the timetable (or maintenance) overlaps the society window. */
    public List<LocalDate> societyExclusions(Room room, LocalDate from, LocalDate to) {
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Slot window = calendar.societyWindow(d);
            boolean blocked = calendar.timetableBusy(room, d).stream().anyMatch(s -> s.overlaps(window))
                    || !availability.roomBusy(room, d, null).stream()
                        .filter(s -> s.start().equals(LocalTime.MIN)).toList().isEmpty();
            if (blocked) out.add(d);
        }
        return out;
    }

    /**
     * Lab assistant emergency booking for a student: overrides the weekly limit (reason
     * mandatory, audited) and is approved immediately if an item is free.
     */
    @Transactional
    public Booking emergencyBooking(User assistant, User student, Long typeId, LocalDate day, String reason) {
        if (assistant.getRole() != Role.LAB_ASSISTANT || assistant.getLab() == null) {
            throw new BookingException("Only a lab assistant can make an emergency booking.");
        }
        if (reason == null || reason.isBlank()) throw new BookingException("An emergency booking needs a reason.");
        if (student == null || !student.isActive()) throw new BookingException("Pick an active student.");
        Lab lab = assistant.getLab();
        EquipmentType type = types.findById(typeId).orElseThrow(() -> new BookingException("Pick an equipment type."));
        if (day == null || day.isBefore(time.today())) throw new BookingException("Pick today or a later date.");
        if (availability.freeItem(lab, type, day, null).isEmpty()) {
            throw new BookingException("No " + type.getName() + " is free on " + D.format(day) + ".");
        }
        requireNoOpenIncident(student, null, lab);
        String overridden = null;
        try {
            checkWeeklyLimit(student, null, type, day);
        } catch (BookingException e) {
            overridden = e.getMessage();
        }
        Booking b = new Booking();
        b.setKind(BookingKind.EQUIPMENT);
        b.setStatus(BookingStatus.PENDING);
        b.setRequester(student);
        b.setLab(lab);
        b.setEquipmentType(type);
        b.setSlotDate(day);
        b.setReason(BookingReason.OTHER);
        b.setReasonText("Emergency: " + reason.trim());
        b.setUrgent(true);
        b.setQueueRank(nextRank());
        b.setCreatedAt(time.now());
        bookings.save(b);
        audit.log(b, assistant, ApprovalAction.REQUESTED, "Created by lab assistant (emergency)");
        if (overridden != null) audit.log(b, assistant, ApprovalAction.LIMIT_OVERRIDE, reason.trim() + " | rule: " + overridden);
        approvals.emergencyApprove(b, assistant, reason.trim());
        return b;
    }

    // ------------------------------------------------------------------ cancellation

    @Transactional
    public void cancel(Booking b, User actor, String note) {
        if (!b.isLive() || b.getStatus() == BookingStatus.CHECKED_OUT) {
            throw new BookingException("Only pending or approved bookings that haven't been picked up can be cancelled.");
        }
        if (!canCancel(b, actor)) throw new BookingException("You can't cancel this booking.");
        boolean wasApproved = b.getStatus() == BookingStatus.APPROVED;
        b.setStatus(BookingStatus.CANCELLED);
        b.setDecisionNote(trim(note));
        bookings.save(b);
        approvals.voidOffers(b);
        audit.log(b, actor, ApprovalAction.CANCELLED, note);
        notifications.notifyBookingParties(b, "Cancelled: " + b.getResourceLabel() + ", " + b.getWhenLabel());
        // Nothing moves automatically: tell whoever manages the slot so they can reassign it.
        String msg = "Freed slot: " + b.getResourceLabel() + ", " + b.getWhenLabel() + " was cancelled. Reassign it if someone is waiting.";
        if (wasApproved) {
            switch (b.getKind()) {
                case EQUIPMENT -> {
                    notifications.notifyAll(users.findByLabAndRoleAndStatus(b.getLab(), Role.LAB_ASSISTANT, UserStatus.ACTIVE), msg, "/assistant/queue");
                    queueEvents.labChanged(b.getLab());
                }
                case GD_ROOM -> notifications.notifyRole(Role.GD_ADMIN, msg, "/gd");
                case SOCIETY_ROOM -> notifications.notifyRole(Role.SOCIETY_ADMIN, msg, "/society-admin");
                case LAB_ROOM -> notifications.notify(b.getRoom().getApprover(), msg, "/faculty/approvals");
            }
        }
    }

    public boolean canCancel(Booking b, User u) {
        if (b.getRequester().sameAs(u)) return true;
        if (b.getGroup() != null && b.getGroup().getLeader().sameAs(u)) return true;
        if (b.getSociety() != null && bearers.existsBySocietyAndUser(b.getSociety(), u)) return true;
        return u.getRole() == Role.SUPER_ADMIN;
    }

    // ------------------------------------------------------------------ rules

    /** Account is active and not under a strike block. */
    public void requireCanBook(User user) {
        if (!user.isActive()) throw new BookingException("Your account is not active.");
        if (user.isBlockedOn(time.today())) {
            throw new BookingException("You are blocked from booking until " + D.format(user.getBlockedUntil()) + " because of strikes.");
        }
    }

    /**
     * Rolling 7-day rule per equipment type. A booking on day d blocks d-6 … d+6, so a
     * Wednesday booking unlocks the next Wednesday. Group use also blocks every member's
     * individual booking that week.
     */
    public void checkWeeklyLimit(User user, StudyGroup group, EquipmentType type, LocalDate day) {
        LocalDate from = day.minusDays(7), to = day.plusDays(7); // exclusive bounds
        if (group != null) {
            if (!bookings.groupEquipmentWithin(List.of(group), type, from, to, Booking.COUNTS_FOR_LIMIT).isEmpty()) {
                throw new BookingException(group.getName() + " already has " + type.getName() + " within 7 days of that date. One per rolling week.");
            }
            return;
        }
        List<Booking> own = bookings.personalEquipmentWithin(user, type, from, to, Booking.COUNTS_FOR_LIMIT);
        if (!own.isEmpty()) {
            LocalDate unlock = own.stream().map(Booking::getSlotDate).max(LocalDate::compareTo).get().plusDays(7);
            throw new BookingException("You already have " + type.getName() + " within 7 days of that date. Next free from " + D.format(unlock) + " (one per rolling week).");
        }
        List<StudyGroup> mine = activeGroups(user);
        if (!mine.isEmpty() && !bookings.groupEquipmentWithin(mine, type, from, to, Booking.COUNTS_FOR_LIMIT).isEmpty()) {
            throw new BookingException("Your group used " + type.getName() + " that week, so members can't book it individually.");
        }
    }

    /** Damage under investigation blocks the requester and all group members from that lab's equipment. */
    public void requireNoOpenIncident(User user, StudyGroup group, Lab lab) {
        Set<Long> people = new HashSet<>();
        people.add(user.getId());
        if (group != null) group.getMembers().forEach(m -> people.add(m.getUser().getId()));
        for (Incident i : incidents.findByLabAndStatus(lab, IncidentStatus.OPEN)) {
            Booking b = i.getBooking();
            if (b == null) continue;
            Set<Long> involved = new HashSet<>();
            involved.add(b.getRequester().getId());
            if (b.getGroup() != null) b.getGroup().getMembers().forEach(m -> involved.add(m.getUser().getId()));
            if (!Collections.disjoint(people, involved)) {
                throw new BookingException("An open damage report blocks you" + (group != null ? " or a group member" : "")
                        + " from " + lab.getName() + " equipment until it is resolved.");
            }
        }
    }

    public List<StudyGroup> activeGroups(User u) {
        return groups.findForMember(u).stream().filter(g -> g.getStatus() == GroupStatus.ACTIVE).toList();
    }

    private StudyGroup resolveGroup(User user, Long groupId) {
        if (groupId == null) return null;
        StudyGroup g = groups.findById(groupId).orElseThrow(() -> new BookingException("Unknown group."));
        if (g.getStatus() != GroupStatus.ACTIVE) throw new BookingException(g.getName() + " is not active yet.");
        if (!g.getLeader().sameAs(user)) throw new BookingException("Only the group leader can book for " + g.getName() + ".");
        return g;
    }

    private void validateTimes(LocalDate date, LocalTime start, LocalTime end) {
        if (date == null || start == null || end == null) throw new BookingException("Pick a date and time.");
        if (!end.isAfter(start)) throw new BookingException("The end time must be after the start time.");
        if (date.atTime(start).isBefore(time.now())) throw new BookingException("That time has passed.");
    }

    private String suggestText(Room room, LocalDate d, LocalTime s, LocalTime e) {
        List<Slot> alt = availability.nearestRoomSlots(room, d, s, e, 3, 3, null);
        if (alt.isEmpty()) return "No free slot nearby.";
        DateTimeFormatter t = DateTimeFormatter.ofPattern("h:mm a");
        return "Nearest free: " + alt.stream().map(x -> D.format(x.date()) + " " + t.format(x.start()) + "–" + t.format(x.end()))
                .collect(Collectors.joining("; ")) + ".";
    }

    /** Ids of the user's active groups; JPQL "in" needs a non-empty list, and -1 never matches. */
    private List<Long> groupIds(User u) {
        List<Long> ids = new ArrayList<>(activeGroups(u).stream().map(StudyGroup::getId).toList());
        if (ids.isEmpty()) ids.add(-1L);
        return ids;
    }

    public static long nextRank() {
        return System.currentTimeMillis() * 1000 + (RANK_SEQ.incrementAndGet() % 1000);
    }

    private static Long idOf(BaseEntity e) {
        return e == null ? null : e.getId();
    }

    private static String trim(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        return s.length() > 490 ? s.substring(0, 490) : s;
    }

    static String joinDates(List<LocalDate> ds) {
        return ds.isEmpty() ? null : ds.stream().map(LocalDate::toString).collect(Collectors.joining(","));
    }
}
