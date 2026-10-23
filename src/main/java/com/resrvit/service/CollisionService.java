package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.engine.Slot;
import com.resrvit.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Changes that can hit already-approved bookings: a new timetable, a declared holiday, or a
 * maintenance block. Each change is first run as a dry run (in a transaction that is rolled back)
 * so the admin sees an impact preview. On confirm, affected bookings get a guaranteed replacement
 * (another item, another day or another slot) or are flagged critical for manual handling.
 * Protected evaluation bookings are never moved: only an equivalent item on the same day is tried.
 */
@Service
@RequiredArgsConstructor
public class CollisionService {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("EEE d MMM");
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("h:mm a");

    private final BookingRepository bookings;
    private final TimetableSlotRepository timetable;
    private final CalendarDayRepository calendarDays;
    private final RoomRepository rooms;
    private final EquipmentRepository equipment;
    private final MaintenanceLogRepository maintenance;
    private final AvailabilityService availability;
    private final CalendarService calendar;
    private final BookingService bookingService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final TimeService time;
    private final PlatformTransactionManager txManager;

    public enum Outcome { NEW_ITEM, NEW_DAY, NEW_SLOT, DAYS_EXCLUDED, CRITICAL }

    public record Impact(Long bookingId, String who, String what, String when, Outcome outcome, String detail) {
        public String getBadge() {
            return outcome == Outcome.CRITICAL ? "danger" : "info";
        }
    }

    public record Result(List<Impact> impacts, List<String> notes) {}

    // ------------------------------------------------------------------ timetable

    public record TimetableRow(String roomCode, int dayOfWeek, LocalTime start, LocalTime end, String course, String batch) {}

    /** Columns: room_code,day,start,end,course_code,batch (day as MON..SUN or 1..7, times as HH:mm). */
    public List<TimetableRow> parseTimetable(String csv, List<String> errors) throws IOException {
        List<TimetableRow> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new StringReader(csv))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null) {
                n++;
                if (line.isBlank()) continue;
                String[] c = Csv.split(line);
                if (n == 1 && Csv.col(c, 0).equalsIgnoreCase("room_code")) continue;
                try {
                    String code = Csv.col(c, 0);
                    if (rooms.findByCodeIgnoreCase(code).isEmpty()) throw new IllegalArgumentException("unknown room " + code);
                    int dow = parseDay(Csv.col(c, 1));
                    LocalTime s = LocalTime.parse(pad(Csv.col(c, 2)));
                    LocalTime e = LocalTime.parse(pad(Csv.col(c, 3)));
                    if (!e.isAfter(s)) throw new IllegalArgumentException("end before start");
                    rows.add(new TimetableRow(code, dow, s, e, Csv.col(c, 4), Csv.col(c, 5)));
                } catch (RuntimeException ex) {
                    errors.add("Line " + n + ": " + ex.getMessage());
                }
            }
        }
        return rows;
    }

    /** Replaces the whole timetable. */
    public Result applyTimetable(List<TimetableRow> rows, User actor, boolean dryRun) {
        return run(dryRun, actor, notes -> {
            timetable.deleteAllInBatch();
            for (TimetableRow row : rows) {
                TimetableSlot t = new TimetableSlot();
                t.setRoom(rooms.findByCodeIgnoreCase(row.roomCode()).orElseThrow());
                t.setDayOfWeek(row.dayOfWeek());
                t.setStartTime(row.start());
                t.setEndTime(row.end());
                t.setCourseCode(blank(row.course()));
                t.setBatchLabel(blank(row.batch()));
                timetable.save(t);
            }
            timetable.flush();
            notes.add(rows.size() + " timetable slots loaded (the previous timetable is replaced).");
        });
    }

    // ------------------------------------------------------------------ academic calendar

    public record CalendarRow(LocalDate date, DayType type, String description) {}

    /** Columns: date,type,description (date as yyyy-MM-dd; type WORKING, HOLIDAY or EXAM). */
    public List<CalendarRow> parseCalendar(String csv, List<String> errors) throws IOException {
        List<CalendarRow> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new StringReader(csv))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null) {
                n++;
                if (line.isBlank()) continue;
                String[] c = Csv.split(line);
                if (n == 1 && Csv.col(c, 0).equalsIgnoreCase("date")) continue;
                try {
                    rows.add(new CalendarRow(LocalDate.parse(Csv.col(c, 0)), DayType.valueOf(Csv.col(c, 1).toUpperCase()), Csv.col(c, 2)));
                } catch (RuntimeException ex) {
                    errors.add("Line " + n + ": " + ex.getMessage());
                }
            }
        }
        return rows;
    }

    /** Adds or updates calendar days (other dates keep their current type). */
    public Result applyCalendar(List<CalendarRow> rows, User actor, boolean dryRun) {
        return run(dryRun, actor, notes -> {
            for (CalendarRow row : rows) {
                CalendarDay d = calendarDays.findByCalDate(row.date()).orElseGet(CalendarDay::new);
                d.setCalDate(row.date());
                d.setDayType(row.type());
                d.setDescription(blank(row.description()));
                calendarDays.save(d);
            }
            calendarDays.flush();
            notes.add(rows.size() + " calendar days saved.");
        });
    }

    // ------------------------------------------------------------------ maintenance

    public Result applyMaintenance(Long equipmentId, Long roomId, LocalDate from, LocalDate to, String reason,
                                   User actor, boolean dryRun) {
        if ((equipmentId == null) == (roomId == null)) throw new BookingException("Pick either an item or a room.");
        if (from == null || to == null || to.isBefore(from)) throw new BookingException("Pick a valid date range.");
        if (reason == null || reason.isBlank()) throw new BookingException("Give a reason.");
        return run(dryRun, actor, notes -> {
            MaintenanceLog m = new MaintenanceLog();
            if (equipmentId != null) m.setEquipment(equipment.findById(equipmentId).orElseThrow(() -> new BookingException("Unknown item.")));
            if (roomId != null) m.setRoom(rooms.findById(roomId).orElseThrow(() -> new BookingException("Unknown room.")));
            m.setStartDate(from);
            m.setEndDate(to);
            m.setReason(reason.trim());
            m.setCreatedBy(actor);
            m.setActive(true);
            m.setCreatedAt(time.now());
            maintenance.saveAndFlush(m);
            notes.add("Maintenance block on " + m.getTargetLabel() + ", " + D.format(from) + " – " + D.format(to) + ".");
        });
    }

    /** Re-checks approved bookings after an item is pulled for maintenance at check-in. */
    public Result revalidateNow(User actor) {
        return run(false, actor, notes -> {});
    }

    // ------------------------------------------------------------------ engine

    private Result run(boolean dryRun, User actor, Consumer<List<String>> change) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        return tx.execute(status -> {
            List<String> notes = new ArrayList<>();
            change.accept(notes);
            List<Impact> impacts = revalidate(actor, dryRun);
            if (dryRun) status.setRollbackOnly();
            return new Result(impacts, notes);
        });
    }

    /** Re-checks every approved booking from today on against the current state and repairs what broke. */
    private List<Impact> revalidate(User actor, boolean dryRun) {
        LocalDate today = time.today();
        List<Impact> impacts = new ArrayList<>();
        for (Booking b : bookings.approvedInRange(today, today.plusDays(180))) {
            Impact i = switch (b.getKind()) {
                case EQUIPMENT -> checkEquipment(b);
                case GD_ROOM, LAB_ROOM -> checkRoom(b);
                case SOCIETY_ROOM -> checkSociety(b);
            };
            if (i == null) continue;
            impacts.add(i);
            bookings.saveAndFlush(b);
            if (!dryRun) {
                audit.log(b, actor, i.outcome() == Outcome.CRITICAL ? ApprovalAction.FLAGGED_CRITICAL : ApprovalAction.MOVED, i.detail());
                notifications.notifyBookingParties(b, (i.outcome() == Outcome.CRITICAL ? "Needs attention: " : "Changed: ")
                        + i.what() + ". " + i.detail());
            }
        }
        if (!dryRun && impacts.stream().anyMatch(i -> i.outcome() == Outcome.CRITICAL)) {
            notifications.notifyRole(Role.SUPER_ADMIN, "Some bookings could not be moved and are flagged critical.", "/admin/critical");
        }
        return impacts;
    }

    private Impact checkEquipment(Booking b) {
        LocalDate day = b.getSlotDate();
        boolean holiday = calendar.isHoliday(day);
        boolean itemOk = b.getEquipment() != null
                && availability.usableItems(b.getLab(), b.getEquipmentType(), day).stream().anyMatch(e -> e.sameAs(b.getEquipment()));
        if (!holiday && itemOk) return null;
        String what = b.getResourceLabel();
        String when = b.getWhenLabel();
        if (!holiday) {
            Optional<Equipment> swap = availability.freeItem(b.getLab(), b.getEquipmentType(), day, b.getId());
            if (swap.isPresent()) {
                String old = b.getEquipment() != null ? b.getEquipment().getAssetTag() : "-";
                b.setEquipment(swap.get());
                return new Impact(b.getId(), b.getForLabel(), what, when, Outcome.NEW_ITEM,
                        "Equivalent item " + swap.get().getAssetTag() + " replaces " + old + ", same day.");
            }
        }
        if (b.isProtectedEval()) return critical(b, what, when, "Protected evaluation booking: it can't be moved, and no equivalent item is free that day.");
        LocalDate from = day.minusDays(7).isBefore(time.today()) ? time.today() : day.minusDays(7);
        LocalDate to = day.plusDays(7);
        if (b.getEvaluation() != null && to.isAfter(b.getEvaluation().getEvalDate().minusDays(1))) to = b.getEvaluation().getEvalDate().minusDays(1);
        for (LocalDate d : availability.nearestFreeDays(b.getLab(), b.getEquipmentType(), day, from, to, day, 3)) {
            Optional<Equipment> item = availability.freeItem(b.getLab(), b.getEquipmentType(), d, b.getId());
            if (item.isPresent()) {
                b.setSlotDate(d);
                b.setEquipment(item.get());
                return new Impact(b.getId(), b.getForLabel(), what, when, Outcome.NEW_DAY,
                        "Moved to " + D.format(d) + " (" + item.get().getAssetTag() + ")" + (holiday ? " because of the holiday." : "."));
            }
        }
        return critical(b, what, when, "No equivalent item free within 7 days.");
    }

    private Impact checkRoom(Booking b) {
        if (availability.isRoomFree(b.getRoom(), b.getSlotDate(), b.getStartTime(), b.getEndTime(), b.getId())) return null;
        String what = b.getResourceLabel();
        String when = b.getWhenLabel();
        for (Slot s : availability.nearestRoomSlots(b.getRoom(), b.getSlotDate(), b.getStartTime(), b.getEndTime(), 7, 5, b.getId())) {
            if (b.getKind() == BookingKind.GD_ROOM && s.date().isAfter(time.today().plusDays(7))) continue;
            b.setSlotDate(s.date());
            b.setStartTime(s.start());
            b.setEndTime(s.end());
            return new Impact(b.getId(), b.getForLabel(), what, when, Outcome.NEW_SLOT,
                    "Moved to " + D.format(s.date()) + ", " + T.format(s.start()) + " – " + T.format(s.end()) + ".");
        }
        return critical(b, what, when, "No free slot in this room within 7 days.");
    }

    private Impact checkSociety(Booking b) {
        LocalDate from = b.getSlotDate().isBefore(time.today()) ? time.today() : b.getSlotDate();
        List<LocalDate> now = new ArrayList<>(b.getExcludedDateList());
        List<LocalDate> fresh = bookingService.societyExclusions(b.getRoom(), from, b.getEndDate());
        List<LocalDate> added = fresh.stream().filter(d -> !now.contains(d)).toList();
        if (added.isEmpty()) return null;
        String when = b.getWhenLabel();
        now.addAll(added);
        Collections.sort(now);
        b.setExcludedDates(BookingService.joinDates(now));
        long total = java.time.temporal.ChronoUnit.DAYS.between(from, b.getEndDate()) + 1;
        long left = total - now.stream().filter(d -> !d.isBefore(from)).count();
        if (left <= 0) return critical(b, b.getResourceLabel(), when, "Every remaining day is now blocked.");
        return new Impact(b.getId(), b.getForLabel(), b.getResourceLabel(), when, Outcome.DAYS_EXCLUDED,
                "No longer available on " + String.join(", ", added.stream().map(D::format).toList()) + "; other days unchanged.");
    }

    private Impact critical(Booking b, String what, String when, String why) {
        b.setCritical(true);
        b.setCriticalNote(why);
        return new Impact(b.getId(), b.getForLabel(), what, when, Outcome.CRITICAL, why + " Flagged critical for manual handling.");
    }

    /** Admin marks a critical booking as handled. */
    public void clearCritical(Booking b, User actor, String note) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            b.setCritical(false);
            b.setCriticalNote(null);
            bookings.save(b);
            audit.log(b, actor, ApprovalAction.MOVED, "Critical flag cleared: " + (note == null ? "" : note));
        });
    }

    public void closeMaintenance(MaintenanceLog m) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            m.setActive(false);
            m.setClosedAt(time.now());
            maintenance.save(m);
            if (m.getEquipment() != null && m.getEquipment().getStatus() == EquipmentStatus.MAINTENANCE) {
                m.getEquipment().setStatus(EquipmentStatus.AVAILABLE);
                equipment.save(m.getEquipment());
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private static int parseDay(String s) {
        s = s.trim().toUpperCase();
        if (s.matches("[1-7]")) return Integer.parseInt(s);
        for (DayOfWeek d : DayOfWeek.values()) {
            if (d.name().startsWith(s) && s.length() >= 3) return d.getValue();
        }
        throw new IllegalArgumentException("unknown day '" + s + "'");
    }

    private static String pad(String t) {
        return t.length() == 4 ? "0" + t : t;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
