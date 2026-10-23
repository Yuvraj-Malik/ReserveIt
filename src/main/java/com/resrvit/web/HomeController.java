package com.resrvit.web;

import com.resrvit.domain.*;
import com.resrvit.engine.Slot;
import com.resrvit.repo.*;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Landing page for each role, plus the super admin's Manage and Oversee pages. */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CurrentUser currentUser;
    private final BookingRepository bookings;
    private final SlotOfferRepository offers;
    private final StudyGroupRepository groups;
    private final LabRepository labs;
    private final RoomRepository rooms;
    private final EquipmentRepository equipment;
    private final CheckoutRepository checkouts;
    private final IncidentRepository incidents;
    private final UserRepository users;
    private final EvaluationScheduleRepository evaluations;
    private final MaintenanceLogRepository maintenance;
    private final CalendarDayRepository calendarDays;
    private final ApprovalRepository approvalLog;
    private final SocietyRepository societies;
    private final SocietyOfficeBearerRepository bearers;
    private final AnnouncementRepository announcements;
    private final ApprovalService approvals;
    private final AvailabilityService availability;
    private final CalendarService calendar;
    private final ChatService chat;
    private final StrikeService strikes;
    private final TimeService time;

    /** One row of "what's free today" for a lab's item type. */
    public record Stock(String lab, String type, int free, int capacity, int waiting) {}

    /** A GD room's day: one cell per fixed slot. */
    public record GdCell(String label, String state, Long bookingId) {}

    public record GdRow(Room room, List<GdCell> cells) {}

    @GetMapping("/")
    public String home(Model model) {
        User me = currentUser.get();
        LocalDate today = time.today();
        model.addAttribute("isHoliday", calendar.isHoliday(today));
        switch (me.getRole()) {
            case STUDENT -> student(me, model, today);
            case FACULTY -> faculty(me, model, today);
            case LAB_ASSISTANT -> assistant(me, model, today);
            case GD_ADMIN -> gdAdmin(me, model, today);
            case SOCIETY_ADMIN -> societyAdmin(me, model, today);
            case SUPER_ADMIN -> superAdmin(model, today);
        }
        return "home";
    }

    // ------------------------------------------------------------------ roles

    private void student(User me, Model model, LocalDate today) {
        model.addAttribute("upcoming", myLive(me));
        model.addAttribute("offers", offers.findOpenForUser(me));
        model.addAttribute("groupInvites", groups.findForMember(me).stream()
                .filter(g -> g.getStatus() == GroupStatus.PENDING_CONFIRMATION)
                .filter(g -> g.getMembers().stream().anyMatch(m -> m.getUser().sameAs(me) && !m.isConfirmed())).toList());
        model.addAttribute("activeStrikes", strikes.activeCount(me));
        model.addAttribute("myGroups", groups.findForMember(me));
        model.addAttribute("evaluations", evaluations.findByEvalDateGreaterThanEqualOrderByEvalDateAsc(today).stream().limit(6).toList());
        model.addAttribute("openChats", chat.inbox(me).stream().filter(t -> !t.locked()).limit(5).toList());
    }

    private void faculty(User me, Model model, LocalDate today) {
        List<Lab> myLabs = labs.findByInCharge(me);
        List<Room> myRooms = rooms.findApprovableBy(me, List.of(RoomType.EVALUATION_LAB, RoomType.SEMINAR_ROOM));
        List<Booking> pending = approvals.pendingFor(me, myLabs, myRooms);
        model.addAttribute("pending", pending);
        model.addAttribute("myLabs", myLabs);
        model.addAttribute("myRooms", myRooms);
        model.addAttribute("stock", stock(myLabs, today));
        model.addAttribute("upcoming", myLive(me));
        model.addAttribute("groupsWaiting", groups.findByCourseFacultyAndStatus(me, GroupStatus.PENDING_FACULTY));
        model.addAttribute("myEvaluations", evaluations.findByFacultyOrderByEvalDateDesc(me).stream()
                .filter(e -> !e.getEvalDate().isBefore(today)).toList());
        model.addAttribute("out", outFrom(myLabs));
        model.addAttribute("roomsToday", myRooms.stream().flatMap(r -> bookings.roomOnDay(r, today, Booking.OCCUPYING).stream()).toList());
    }

    private void assistant(User me, Model model, LocalDate today) {
        Lab lab = me.getLab();
        model.addAttribute("lab", lab);
        if (lab == null) return;
        List<Booking> pending = bookings.pendingEquipmentForLabs(List.of(lab));
        model.addAttribute("pickups", bookings.approvedEquipmentForLabOn(lab, today));
        model.addAttribute("pendingCount", pending.size());
        model.addAttribute("fallback", pending.stream().filter(approvals::fallbackAllowed).toList());
        List<Checkout> out = outFrom(List.of(lab));
        model.addAttribute("out", out);
        model.addAttribute("overdue", out.stream().filter(c -> c.getDueAt().isBefore(time.now())).count());
        model.addAttribute("openIncidents", incidents.findByLabAndStatus(lab, IncidentStatus.OPEN));
        model.addAttribute("stock", stock(List.of(lab), today));
        model.addAttribute("maintenanceNow", maintenance.findByActiveTrueOrderByStartDateAsc().stream()
                .filter(m -> m.getEquipment() != null && m.getEquipment().getLab().sameAs(lab)).toList());
        model.addAttribute("chats", chat.inbox(me).stream().limit(5).toList());
        model.addAttribute("evaluations", evaluations.findByLabAndEvalDateGreaterThanOrderByEvalDateAsc(lab, today.minusDays(1)));
    }

    private void gdAdmin(User me, Model model, LocalDate today) {
        List<Booking> pending = bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.GD_ROOM, BookingStatus.PENDING);
        model.addAttribute("pending", pending);
        model.addAttribute("claimedByMe", pending.stream().filter(b -> b.getClaimedBy() != null && b.getClaimedBy().sameAs(me)).count());
        model.addAttribute("unclaimed", pending.stream().filter(b -> b.getClaimedBy() == null).count());
        model.addAttribute("gdToday", gdGrid(today));
        LocalDate tomorrow = today.plusDays(1);
        model.addAttribute("gdTomorrow", gdGrid(tomorrow));
        model.addAttribute("tomorrow", tomorrow);
        List<Booking> approved = bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.GD_ROOM, BookingStatus.APPROVED);
        model.addAttribute("weekApproved", approved.stream().filter(b -> !b.getSlotDate().isBefore(today) && b.getSlotDate().isBefore(today.plusDays(7))).count());
        model.addAttribute("freed", bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.GD_ROOM, BookingStatus.CANCELLED).stream()
                .filter(b -> b.getDecidedAt() != null && !b.getSlotDate().isBefore(today)).toList());
        model.addAttribute("chats", chat.inbox(me).stream().limit(5).toList());
    }

    private void societyAdmin(User me, Model model, LocalDate today) {
        List<Booking> pending = bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.SOCIETY_ROOM, BookingStatus.PENDING);
        List<Booking> active = bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.SOCIETY_ROOM, BookingStatus.APPROVED).stream()
                .filter(b -> !b.getEndDate().isBefore(today)).sorted(Comparator.comparing(Booking::getEndDate)).toList();
        model.addAttribute("pending", pending);
        model.addAttribute("renewals", pending.stream().filter(b -> b.getPreviousBooking() != null).count());
        model.addAttribute("claimedByMe", pending.stream().filter(b -> b.getClaimedBy() != null && b.getClaimedBy().sameAs(me)).count());
        model.addAttribute("active", active);
        model.addAttribute("ending", active.stream().filter(b -> !b.getEndDate().isAfter(today.plusDays(7))).toList());
        model.addAttribute("tonight", active.stream().filter(b -> !b.getSlotDate().isAfter(today) && !b.getExcludedDateList().contains(today)).toList());
        model.addAttribute("window", calendar.societyWindow(today));
        model.addAttribute("societyCount", societies.findByActiveTrueOrderByNameAsc().size());
        model.addAttribute("bearerCount", bearers.count());
        model.addAttribute("socRooms", rooms.findByTypeAndBookableTrueOrderByNameAsc(RoomType.SOCIETY_ROOM));
        model.addAttribute("chats", chat.inbox(me).stream().limit(5).toList());
    }

    private void superAdmin(Model model, LocalDate today) {
        List<User> all = users.findAllByOrderByRoleAscFullNameAsc();
        Map<Role, Long> byRole = new EnumMap<>(Role.class);
        for (Role r : Role.values()) byRole.put(r, all.stream().filter(u -> u.getRole() == r && u.isActive()).count());
        List<Booking> pending = bookings.findByStatusOrderByQueueRankAsc(BookingStatus.PENDING);
        Map<BookingKind, Long> pendingByKind = new EnumMap<>(BookingKind.class);
        for (BookingKind k : BookingKind.values()) pendingByKind.put(k, pending.stream().filter(b -> b.getKind() == k).count());
        List<Checkout> out = checkouts.findByReturnedAtIsNullOrderByDueAtAsc();
        model.addAttribute("byRole", byRole);
        model.addAttribute("pendingByKind", pendingByKind);
        model.addAttribute("pendingAll", pending.size());
        model.addAttribute("awaiting", users.findByStatusOrderByCreatedAtAsc(UserStatus.PENDING_ACTIVATION));
        model.addAttribute("critical", bookings.criticalBookings(Booking.LIVE));
        model.addAttribute("outNow", out.size());
        model.addAttribute("overdue", out.stream().filter(c -> c.getDueAt().isBefore(time.now())).toList());
        model.addAttribute("openIncidents", incidents.findByStatusOrderByCreatedAtDesc(IncidentStatus.OPEN));
        model.addAttribute("maintenanceNow", maintenance.findByActiveTrueOrderByStartDateAsc());
        model.addAttribute("todayCount", bookings.approvedInRange(today, today).size());
        model.addAttribute("holidays", calendarDays.findByCalDateBetweenOrderByCalDateAsc(today, today.plusDays(45)).stream()
                .filter(d -> d.getDayType() != DayType.WORKING).toList());
        model.addAttribute("activity", approvalLog.findTop50ByOrderByCreatedAtDesc().stream().limit(10).toList());
        model.addAttribute("stock", stock(labs.findAllByOrderByNameAsc(), today));
        model.addAttribute("recentAnnouncements", announcements.findTop50ByOrderByCreatedAtDesc().stream().limit(3).toList());
        model.addAttribute("userCount", all.size());
    }

    // ------------------------------------------------------------------ hub pages (no dropdowns)

    @GetMapping("/admin/manage")
    public String manage(Model model) {
        model.addAttribute("pendingUsers", users.findByStatusOrderByCreatedAtAsc(UserStatus.PENDING_ACTIVATION).size());
        model.addAttribute("userCount", users.count());
        model.addAttribute("labCount", labs.count());
        model.addAttribute("roomCount", rooms.count());
        model.addAttribute("itemCount", equipment.count());
        model.addAttribute("maintenanceCount", maintenance.findByActiveTrueOrderByStartDateAsc().size());
        model.addAttribute("calendarCount", calendarDays.count());
        return "admin/manage";
    }

    @GetMapping("/admin/oversee")
    public String oversee(Model model) {
        LocalDate today = time.today();
        model.addAttribute("pendingAll", bookings.findByStatusOrderByQueueRankAsc(BookingStatus.PENDING).size());
        model.addAttribute("critical", bookings.criticalBookings(Booking.LIVE).size());
        model.addAttribute("labPending", bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.LAB_ROOM, BookingStatus.PENDING).size()
                + bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.EQUIPMENT, BookingStatus.PENDING).size());
        model.addAttribute("eqPending", bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.EQUIPMENT, BookingStatus.PENDING).size());
        model.addAttribute("gdPending", bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.GD_ROOM, BookingStatus.PENDING).size());
        model.addAttribute("socPending", bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.SOCIETY_ROOM, BookingStatus.PENDING).size());
        model.addAttribute("societyCount", societies.count());
        model.addAttribute("strikeCount", strikes.activeTotal());
        model.addAttribute("incidentCount", incidents.findByStatusOrderByCreatedAtDesc(IncidentStatus.OPEN).size());
        model.addAttribute("todayCount", bookings.approvedInRange(today, today).size());
        return "admin/oversee";
    }

    // ------------------------------------------------------------------ helpers

    private List<Stock> stock(List<Lab> list, LocalDate day) {
        List<Stock> out = new ArrayList<>();
        boolean closed = calendar.isHoliday(day);
        for (Lab l : list) {
            for (EquipmentType t : equipment.typesInLab(l)) {
                int cap = closed ? 0 : availability.capacity(l, t, day);
                out.add(new Stock(l.getName(), t.getName(), closed ? 0 : availability.free(l, t, day), cap,
                        bookings.equipmentOnDay(l, t, day, List.of(BookingStatus.PENDING)).size()));
            }
        }
        return out;
    }

    private List<Checkout> outFrom(List<Lab> list) {
        return checkouts.findByReturnedAtIsNullOrderByDueAtAsc().stream()
                .filter(c -> list.stream().anyMatch(l -> l.sameAs(c.getBooking().getLab()))).toList();
    }

    private List<GdRow> gdGrid(LocalDate day) {
        List<GdRow> rows = new ArrayList<>();
        for (Room r : rooms.findByTypeAndBookableTrueOrderByNameAsc(RoomType.GD_ROOM)) {
            List<Slot> classes = calendar.timetableBusy(r, day);
            List<Booking> live = bookings.roomOnDay(r, day, Booking.LIVE);
            List<GdCell> cells = new ArrayList<>();
            for (LocalTime[] s : CalendarService.GD_SLOTS) {
                Slot slot = new Slot(day, s[0], s[1]);
                Booking b = live.stream().filter(x -> x.getStartTime().equals(s[0])).findFirst().orElse(null);
                if (classes.stream().anyMatch(c -> c.overlaps(slot))) cells.add(new GdCell("Class", "class", null));
                else if (b != null) cells.add(new GdCell(b.getForLabel(), b.isPending() ? "pending" : "booked", b.getId()));
                else cells.add(new GdCell("Free", "free", null));
            }
            rows.add(new GdRow(r, cells));
        }
        return rows;
    }

    private List<Booking> myLive(User me) {
        List<Booking> list = new ArrayList<>(bookings.findByRequesterOrderByCreatedAtDesc(me).stream().filter(Booking::isLive).toList());
        List<StudyGroup> gs = groups.findForMember(me);
        if (!gs.isEmpty()) {
            for (Booking b : bookings.findByGroups(gs)) {
                if (b.isLive() && list.stream().noneMatch(x -> x.getId().equals(b.getId()))) list.add(b);
            }
        }
        list.sort(Comparator.comparing(Booking::getSlotDate));
        return list;
    }
}
