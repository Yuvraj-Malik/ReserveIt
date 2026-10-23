package com.resrvit.web;

import com.resrvit.domain.*;
import com.resrvit.engine.Slot;
import com.resrvit.repo.*;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Small JSON endpoints for the booking forms and the FullCalendar view. */
@Controller
@RequiredArgsConstructor
public class ApiController {

    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("h:mm a");

    private final LabRepository labs;
    private final RoomRepository rooms;
    private final EquipmentRepository equipment;
    private final EquipmentTypeRepository types;
    private final BookingRepository bookings;
    private final MaintenanceLogRepository maintenance;
    private final AvailabilityService availability;
    private final CalendarService calendar;
    private final BookingService bookingService;
    private final SettingsService settings;
    private final TimeService time;

    @GetMapping("/calendar")
    public String calendarPage(Model model) {
        model.addAttribute("rooms", rooms.findAllByOrderByNameAsc().stream().filter(Room::isBookable).toList());
        model.addAttribute("labs", labs.findAllByOrderByNameAsc());
        return "calendar";
    }

    @GetMapping("/api/labs/{id}/types")
    @ResponseBody
    public List<Map<String, Object>> labTypes(@PathVariable Long id) {
        Lab lab = labs.findById(id).orElseThrow(() -> new NotFoundException("Lab"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (EquipmentType t : equipment.typesInLab(lab)) {
            long usable = equipment.findByLabAndType(lab, t).stream().filter(e -> e.getStatus() == EquipmentStatus.AVAILABLE).count();
            out.add(Map.of("id", t.getId(), "name", t.getName(), "items", usable));
        }
        return out;
    }

    /** Free items per day from today to the lead-time limit. */
    @GetMapping("/api/availability/equipment")
    @ResponseBody
    public List<Map<String, Object>> equipmentAvailability(@RequestParam Long labId, @RequestParam Long typeId) {
        Lab lab = labs.findById(labId).orElseThrow(() -> new NotFoundException("Lab"));
        EquipmentType type = types.findById(typeId).orElseThrow(() -> new NotFoundException("Type"));
        int lead = settings.getInt(SettingsService.LEAD_DAYS, 7);
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = time.today(); !d.isAfter(time.today().plusDays(lead)); d = d.plusDays(1)) {
            // Labs are closed on holidays, and today's pick-up window closes at 5 PM.
            boolean holiday = calendar.isHoliday(d)
                    || (d.equals(time.today()) && time.now().toLocalTime().isAfter(CalendarService.LAB_CLOSE));
            int cap = availability.capacity(lab, type, d);
            int used = availability.used(lab, type, d);
            int waiting = bookings.equipmentOnDay(lab, type, d, List.of(BookingStatus.PENDING)).size();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.toString());
            m.put("label", DateTimeFormatter.ofPattern("EEE d").format(d));
            m.put("holiday", holiday);
            m.put("capacity", cap);
            m.put("free", holiday ? 0 : Math.max(0, cap - used));
            m.put("waiting", waiting);
            out.add(m);
        }
        return out;
    }

    /** What occupies a room on a day (for the room request form). */
    @GetMapping("/api/rooms/{id}/busy")
    @ResponseBody
    public List<Map<String, String>> roomBusy(@PathVariable Long id, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Room room = rooms.findById(id).orElseThrow(() -> new NotFoundException("Room"));
        List<Map<String, String>> out = new ArrayList<>();
        for (Slot s : availability.roomBusy(room, date, null)) {
            boolean allDay = s.start().equals(LocalTime.MIN);
            out.add(Map.of("start", s.start().toString(), "end", s.end().toString(),
                    "label", allDay ? "All day (maintenance)" : T.format(s.start()) + " – " + T.format(s.end())));
        }
        out.sort(Comparator.comparing(m -> m.get("start")));
        return out;
    }

    @GetMapping("/api/society-exclusions")
    @ResponseBody
    public Map<String, Object> societyExclusions(@RequestParam Long roomId,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Room room = rooms.findById(roomId).orElseThrow(() -> new NotFoundException("Room"));
        if (to.isBefore(from) || to.isAfter(from.plusDays(BookingService.MAX_SOCIETY_DAYS + 5))) return Map.of("excluded", List.of(), "days", 0);
        List<String> ex = bookingService.societyExclusions(room, from, to).stream()
                .map(d -> DateTimeFormatter.ofPattern("EEE d MMM").format(d)).toList();
        List<String> windows = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to) && windows.size() < 3; d = d.plusDays(1)) {
            Slot w = calendar.societyWindow(d);
            windows.add(DateTimeFormatter.ofPattern("EEE d MMM").format(d) + ": " + T.format(w.start()) + " – " + T.format(w.end()));
        }
        return Map.of("excluded", ex, "days", java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1, "sampleWindows", windows);
    }

    /** FullCalendar feed: a room's classes, bookings and maintenance, or a lab item type's daily load. */
    @GetMapping("/api/events")
    @ResponseBody
    public List<Map<String, Object>> events(@RequestParam(required = false) Long roomId,
                                            @RequestParam(required = false) Long labId, @RequestParam(required = false) Long typeId,
                                            @RequestParam String start, @RequestParam String end) {
        LocalDate from = parse(start), to = parse(end).minusDays(1);
        if (to.isAfter(from.plusDays(62))) to = from.plusDays(62);
        List<Map<String, Object>> out = new ArrayList<>();
        if (roomId != null) {
            Room room = rooms.findById(roomId).orElseThrow(() -> new NotFoundException("Room"));
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (calendar.isHoliday(d)) out.add(allDay(d, "Holiday", "#94a3b8"));
                for (Slot s : calendar.timetableBusy(room, d)) out.add(timed(s.date(), s.start(), s.end(), "Class", "#64748b"));
                for (Booking b : bookings.roomOnDay(room, d, Booking.LIVE)) {
                    out.add(timed(d, b.getStartTime(), b.getEndTime(), (b.isPending() ? "Requested: " : "") + b.getForLabel(),
                            b.isPending() ? "#d97706" : "#2563eb"));
                }
                for (Booking b : bookings.societyOverlapping(room, d, d, Booking.LIVE)) {
                    if (b.getExcludedDateList().contains(d)) continue;
                    Slot w = calendar.societyWindow(d);
                    out.add(timed(d, w.start(), w.end(), (b.isPending() ? "Requested: " : "") + b.getSociety().getName(),
                            b.isPending() ? "#d97706" : "#7c3aed"));
                }
            }
            for (MaintenanceLog m : maintenance.activeForRoom(room, from, to)) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("title", "Maintenance: " + m.getReason());
                e.put("start", m.getStartDate().toString());
                e.put("end", m.getEndDate().plusDays(1).toString());
                e.put("allDay", true);
                e.put("color", "#dc2626");
                out.add(e);
            }
        } else if (labId != null && typeId != null) {
            Lab lab = labs.findById(labId).orElseThrow(() -> new NotFoundException("Lab"));
            EquipmentType type = types.findById(typeId).orElseThrow(() -> new NotFoundException("Type"));
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (calendar.isHoliday(d)) {
                    out.add(allDay(d, "Lab closed", "#94a3b8"));
                    continue;
                }
                int cap = availability.capacity(lab, type, d);
                int used = availability.used(lab, type, d);
                int waiting = bookings.equipmentOnDay(lab, type, d, List.of(BookingStatus.PENDING)).size();
                String color = cap == 0 ? "#dc2626" : used >= cap ? "#d97706" : "#16a34a";
                out.add(allDay(d, Math.max(0, cap - used) + " of " + cap + " free" + (waiting > 0 ? " · " + waiting + " waiting" : ""), color));
            }
        }
        return out;
    }

    private static LocalDate parse(String s) {
        if (s.length() == 10) return LocalDate.parse(s);
        try {
            return OffsetDateTime.parse(s).toLocalDate();
        } catch (Exception e) {
            return LocalDateTime.parse(s.substring(0, 19)).toLocalDate();
        }
    }

    private static Map<String, Object> allDay(LocalDate d, String title, String color) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("title", title);
        e.put("start", d.toString());
        e.put("allDay", true);
        e.put("color", color);
        return e;
    }

    private static Map<String, Object> timed(LocalDate d, LocalTime s, LocalTime en, String title, String color) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("title", title);
        e.put("start", d.atTime(s).toString());
        e.put("end", d.atTime(en).toString());
        e.put("color", color);
        return e;
    }
}
