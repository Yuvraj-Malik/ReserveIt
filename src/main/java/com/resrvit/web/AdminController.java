package com.resrvit.web;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Super admin: accounts, campus data, timetable and calendar uploads, settings, critical bookings, audit. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CurrentUser currentUser;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final LabRepository labs;
    private final RoomRepository rooms;
    private final EquipmentTypeRepository types;
    private final EquipmentRepository equipment;
    private final TimetableSlotRepository timetable;
    private final CalendarDayRepository calendarDays;
    private final BookingRepository bookings;
    private final ApprovalRepository approvalLog;
    private final QueueAuditRepository queueAudit;
    private final AccountService accounts;
    private final CollisionService collisions;
    private final SettingsService settings;

    // ------------------------------------------------------------------ users

    @GetMapping("/users")
    public String usersPage(@RequestParam(required = false) UserStatus status, @RequestParam(required = false) Role role, Model model) {
        List<User> list = users.findAllByOrderByRoleAscFullNameAsc().stream()
                .filter(u -> status == null || u.getStatus() == status)
                .filter(u -> role == null || u.getRole() == role).toList();
        model.addAttribute("list", list);
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("roles", Role.values());
        model.addAttribute("labs", labs.findAllByOrderByNameAsc());
        model.addAttribute("status", status);
        model.addAttribute("role", role);
        return "admin/users";
    }

    @PostMapping("/users/{id}/activate")
    public String activate(@PathVariable Long id, @RequestHeader(value = "Referer", required = false) String ref, RedirectAttributes ra) {
        return Flash.run(ra, Flash.back(ref, "/admin/users"), () -> accounts.activate(user(id), currentUser.get()), "Account activated.");
    }

    @PostMapping("/users/{id}/deactivate")
    public String deactivate(@PathVariable Long id, @RequestHeader(value = "Referer", required = false) String ref, RedirectAttributes ra) {
        return Flash.run(ra, Flash.back(ref, "/admin/users"), () -> accounts.deactivate(user(id), currentUser.get()),
                "Deactivated. Their live bookings were cancelled; history is kept.");
    }

    @PostMapping("/users/{id}/role")
    public String role(@PathVariable Long id, @RequestParam Role role, @RequestParam(required = false) Long labId,
                       @RequestHeader(value = "Referer", required = false) String ref, RedirectAttributes ra) {
        return Flash.run(ra, Flash.back(ref, "/admin/users"), () -> accounts.changeRole(user(id), role, labId, currentUser.get()), "Role updated.");
    }

    @PostMapping("/users/{id}/reset-password")
    public String reset(@PathVariable Long id, RedirectAttributes ra) {
        return Flash.call(ra, "/admin/users", () -> "Temporary password for " + user(id).getEmail() + ": " + accounts.resetPassword(user(id))
                + " (also emailed).");
    }

    @GetMapping("/import")
    public String importForm() {
        return "admin/import";
    }

    @PostMapping("/import")
    public String importCsv(@RequestParam MultipartFile file, Model model, RedirectAttributes ra) throws IOException {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Choose a CSV file.");
            return "redirect:/admin/import";
        }
        model.addAttribute("rows", accounts.importCsv(file.getInputStream(), currentUser.get()));
        return "admin/import";
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User"));
    }

    // ------------------------------------------------------------------ campus data

    @GetMapping("/campus")
    public String campus(Model model) {
        model.addAttribute("departments", departments.findAllByOrderByNameAsc());
        model.addAttribute("labs", labs.findAllByOrderByNameAsc());
        model.addAttribute("rooms", rooms.findAllByOrderByNameAsc());
        model.addAttribute("types", types.findAllByOrderByNameAsc());
        model.addAttribute("items", equipment.findAllByOrderByAssetTagAsc());
        model.addAttribute("faculty", users.activeFaculty());
        model.addAttribute("roomTypes", RoomType.values());
        model.addAttribute("itemStatuses", EquipmentStatus.values());
        return "admin/campus";
    }

    @PostMapping("/departments")
    @Transactional
    public String saveDepartment(@RequestParam(required = false) Long id, @RequestParam String code, @RequestParam String name, RedirectAttributes ra) {
        return Flash.run(ra, "/admin/campus#departments", () -> {
            Department d = id == null ? new Department() : departments.findById(id).orElseThrow(() -> new NotFoundException("Department"));
            requireText(code, "code");
            requireText(name, "name");
            departments.findByCodeIgnoreCase(code.trim()).filter(o -> !o.getId().equals(id))
                    .ifPresent(o -> { throw new BookingException("Code " + code + " is taken."); });
            d.setCode(code.trim().toUpperCase());
            d.setName(name.trim());
            departments.save(d);
        }, "Department saved.");
    }

    @PostMapping("/labs")
    @Transactional
    public String saveLab(@RequestParam(required = false) Long id, @RequestParam String code, @RequestParam String name,
                          @RequestParam(required = false) Long departmentId, @RequestParam(required = false) Long inChargeId, RedirectAttributes ra) {
        return Flash.run(ra, "/admin/campus#labs", () -> {
            Lab l = id == null ? new Lab() : labs.findById(id).orElseThrow(() -> new NotFoundException("Lab"));
            requireText(code, "code");
            requireText(name, "name");
            labs.findByCodeIgnoreCase(code.trim()).filter(o -> !o.getId().equals(id))
                    .ifPresent(o -> { throw new BookingException("Code " + code + " is taken."); });
            l.setCode(code.trim().toUpperCase());
            l.setName(name.trim());
            l.setDepartment(departmentId == null ? null : departments.findById(departmentId).orElse(null));
            l.setInCharge(inChargeId == null ? null : users.findById(inChargeId).filter(u -> u.getRole() == Role.FACULTY)
                    .orElseThrow(() -> new BookingException("The in-charge must be a faculty member.")));
            labs.save(l);
        }, "Lab saved.");
    }

    @PostMapping("/rooms")
    @Transactional
    public String saveRoom(@RequestParam(required = false) Long id, @RequestParam String code, @RequestParam String name,
                           @RequestParam RoomType type, @RequestParam(required = false) Long departmentId,
                           @RequestParam(required = false) Long labId, @RequestParam(required = false) Long inChargeId,
                           @RequestParam(defaultValue = "0") int capacity, @RequestParam(defaultValue = "false") boolean bookable,
                           RedirectAttributes ra) {
        return Flash.run(ra, "/admin/campus#rooms", () -> {
            Room r = id == null ? new Room() : rooms.findById(id).orElseThrow(() -> new NotFoundException("Room"));
            requireText(code, "code");
            requireText(name, "name");
            rooms.findByCodeIgnoreCase(code.trim()).filter(o -> !o.getId().equals(id))
                    .ifPresent(o -> { throw new BookingException("Code " + code + " is taken."); });
            r.setCode(code.trim().toUpperCase());
            r.setName(name.trim());
            r.setType(type);
            r.setDepartment(departmentId == null ? null : departments.findById(departmentId).orElse(null));
            r.setLab(labId == null ? null : labs.findById(labId).orElse(null));
            r.setInCharge(inChargeId == null ? null : users.findById(inChargeId).orElse(null));
            r.setCapacity(Math.max(0, capacity));
            // Permanently assigned rooms (an HOD office, say) are never bookable.
            r.setBookable(bookable && type != RoomType.PERMANENT);
            rooms.save(r);
        }, "Room saved.");
    }

    @PostMapping("/equipment-types")
    @Transactional
    public String saveType(@RequestParam(required = false) Long id, @RequestParam String name, @RequestParam(required = false) String description,
                           RedirectAttributes ra) {
        return Flash.run(ra, "/admin/campus#equipment", () -> {
            EquipmentType t = id == null ? new EquipmentType() : types.findById(id).orElseThrow(() -> new NotFoundException("Type"));
            requireText(name, "name");
            types.findByNameIgnoreCase(name.trim()).filter(o -> !o.getId().equals(id))
                    .ifPresent(o -> { throw new BookingException("Type " + name + " exists."); });
            t.setName(name.trim());
            t.setDescription(description == null || description.isBlank() ? null : description.trim());
            types.save(t);
        }, "Equipment type saved.");
    }

    @PostMapping("/equipment")
    @Transactional
    public String saveItem(@RequestParam(required = false) Long id, @RequestParam String assetTag, @RequestParam Long typeId,
                           @RequestParam Long labId, @RequestParam EquipmentStatus status, @RequestParam(required = false) String conditionNote,
                           RedirectAttributes ra) {
        return Flash.run(ra, "/admin/campus#equipment", () -> {
            Equipment e = id == null ? new Equipment() : equipment.findById(id).orElseThrow(() -> new NotFoundException("Item"));
            requireText(assetTag, "asset tag");
            equipment.findByAssetTagIgnoreCase(assetTag.trim()).filter(o -> !o.getId().equals(id))
                    .ifPresent(o -> { throw new BookingException("Asset tag " + assetTag + " is taken."); });
            e.setAssetTag(assetTag.trim().toUpperCase());
            e.setType(types.findById(typeId).orElseThrow(() -> new BookingException("Pick a type.")));
            e.setLab(labs.findById(labId).orElseThrow(() -> new BookingException("Pick a lab.")));
            e.setStatus(status);
            e.setConditionNote(conditionNote == null || conditionNote.isBlank() ? null : conditionNote.trim());
            equipment.save(e);
        }, "Item saved.");
    }

    private static void requireText(String s, String what) {
        if (s == null || s.isBlank()) throw new BookingException("Enter a " + what + ".");
    }

    // ------------------------------------------------------------------ timetable and calendar

    @GetMapping("/timetable")
    public String timetablePage(Model model) {
        model.addAttribute("slots", timetable.findAllByOrderByRoomIdAscDayOfWeekAscStartTimeAsc());
        return "admin/timetable";
    }

    @PostMapping("/timetable/preview")
    public String timetablePreview(@RequestParam(required = false) MultipartFile file, @RequestParam(required = false) String csv,
                                   Model model, RedirectAttributes ra) throws IOException {
        String text = file != null && !file.isEmpty() ? new String(file.getBytes(), StandardCharsets.UTF_8) : csv;
        if (text == null || text.isBlank()) {
            ra.addFlashAttribute("error", "Choose a CSV file.");
            return "redirect:/admin/timetable";
        }
        List<String> errors = new ArrayList<>();
        List<CollisionService.TimetableRow> rows = collisions.parseTimetable(text, errors);
        if (!errors.isEmpty()) {
            ra.addFlashAttribute("error", "Fix these lines and upload again: " + String.join("; ", errors.subList(0, Math.min(8, errors.size()))));
            return "redirect:/admin/timetable";
        }
        model.addAttribute("result", collisions.applyTimetable(rows, currentUser.get(), true));
        model.addAttribute("title", "New timetable: impact preview");
        model.addAttribute("confirmAction", "/admin/timetable/confirm");
        model.addAttribute("hidden", Map.of("csv", text));
        model.addAttribute("back", "/admin/timetable");
        return "impact";
    }

    @PostMapping("/timetable/confirm")
    public String timetableConfirm(@RequestParam String csv, RedirectAttributes ra) throws IOException {
        List<String> errors = new ArrayList<>();
        List<CollisionService.TimetableRow> rows = collisions.parseTimetable(csv, errors);
        return Flash.call(ra, "/admin/timetable", () -> {
            if (!errors.isEmpty()) throw new BookingException("The CSV has errors.");
            CollisionService.Result r = collisions.applyTimetable(rows, currentUser.get(), false);
            return "Timetable replaced (" + rows.size() + " slots). " + r.impacts().size() + " booking(s) adjusted.";
        });
    }

    @GetMapping("/calendar")
    public String calendarPage(Model model) {
        model.addAttribute("days", calendarDays.findAllByOrderByCalDateAsc());
        return "admin/calendar";
    }

    @PostMapping("/calendar/preview")
    public String calendarPreview(@RequestParam(required = false) MultipartFile file, @RequestParam(required = false) String csv,
                                  Model model, RedirectAttributes ra) throws IOException {
        String text = file != null && !file.isEmpty() ? new String(file.getBytes(), StandardCharsets.UTF_8) : csv;
        if (text == null || text.isBlank()) {
            ra.addFlashAttribute("error", "Choose a CSV file or add a day.");
            return "redirect:/admin/calendar";
        }
        List<String> errors = new ArrayList<>();
        List<CollisionService.CalendarRow> rows = collisions.parseCalendar(text, errors);
        if (!errors.isEmpty()) {
            ra.addFlashAttribute("error", "Fix these lines and upload again: " + String.join("; ", errors.subList(0, Math.min(8, errors.size()))));
            return "redirect:/admin/calendar";
        }
        model.addAttribute("result", collisions.applyCalendar(rows, currentUser.get(), true));
        model.addAttribute("title", "Academic calendar change: impact preview");
        model.addAttribute("confirmAction", "/admin/calendar/confirm");
        model.addAttribute("hidden", Map.of("csv", text));
        model.addAttribute("back", "/admin/calendar");
        return "impact";
    }

    @PostMapping("/calendar/confirm")
    public String calendarConfirm(@RequestParam String csv, RedirectAttributes ra) throws IOException {
        List<String> errors = new ArrayList<>();
        List<CollisionService.CalendarRow> rows = collisions.parseCalendar(csv, errors);
        return Flash.call(ra, "/admin/calendar", () -> {
            if (!errors.isEmpty()) throw new BookingException("The CSV has errors.");
            CollisionService.Result r = collisions.applyCalendar(rows, currentUser.get(), false);
            return "Calendar updated (" + rows.size() + " days). " + r.impacts().size() + " booking(s) adjusted.";
        });
    }

    // ------------------------------------------------------------------ critical, settings, audit, bookings

    @GetMapping("/critical")
    public String critical(Model model) {
        model.addAttribute("list", bookings.criticalBookings(Booking.LIVE));
        return "admin/critical";
    }

    @PostMapping("/critical/{id}/clear")
    public String clearCritical(@PathVariable Long id, @RequestParam(required = false) String note, RedirectAttributes ra) {
        Booking b = bookings.findById(id).orElseThrow(() -> new NotFoundException("Booking"));
        return Flash.run(ra, "/admin/critical", () -> collisions.clearCritical(b, currentUser.get(), note), "Marked handled.");
    }

    @GetMapping("/settings")
    public String settingsPage() {
        return "redirect:/settings#system";
    }

    @PostMapping("/settings")
    public String saveSetting(@RequestParam String key, @RequestParam String value, RedirectAttributes ra) {
        return Flash.run(ra, "/settings#system", () -> {
            if (value == null || value.isBlank()) throw new BookingException("Enter a value.");
            settings.set(key, value);
        }, "Setting saved.");
    }

    @GetMapping("/audit")
    public String audit(Model model) {
        model.addAttribute("decisions", approvalLog.findTop50ByOrderByCreatedAtDesc());
        model.addAttribute("moves", queueAudit.findTop100ByOrderByCreatedAtDesc());
        return "admin/audit";
    }

    @GetMapping("/bookings")
    public String allBookings(@RequestParam(required = false) BookingStatus status, @RequestParam(required = false) BookingKind kind, Model model) {
        model.addAttribute("list", bookings.findAllByOrderByCreatedAtDesc().stream()
                .filter(b -> status == null || b.getStatus() == status)
                .filter(b -> kind == null || b.getKind() == kind).limit(300).toList());
        model.addAttribute("statuses", BookingStatus.values());
        model.addAttribute("kinds", BookingKind.values());
        model.addAttribute("status", status);
        model.addAttribute("kind", kind);
        return "admin/bookings";
    }
}
