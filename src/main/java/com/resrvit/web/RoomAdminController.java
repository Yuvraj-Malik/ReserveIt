package com.resrvit.web;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Shared queues for GD Room Admins and Society Room Admins, plus society registration. */
@Controller
@RequiredArgsConstructor
public class RoomAdminController {

    private final CurrentUser currentUser;
    private final BookingRepository bookings;
    private final SocietyRepository societyRepo;
    private final SocietyOfficeBearerRepository bearers;
    private final SocietyService societies;
    private final TimeService time;

    @GetMapping("/gd")
    public String gd(Model model) {
        var today = time.today();
        model.addAttribute("pending", bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.GD_ROOM, BookingStatus.PENDING));
        model.addAttribute("approved", bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.GD_ROOM, BookingStatus.APPROVED)
                .stream().filter(b -> !b.getSlotDate().isBefore(today)).sorted(java.util.Comparator.comparing(Booking::getStartsAt)).toList());
        model.addAttribute("freed", bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.GD_ROOM, BookingStatus.CANCELLED)
                .stream().filter(b -> b.getDecidedAt() != null && !b.getSlotDate().isBefore(today)).toList());
        return "admin/gd";
    }

    @GetMapping("/society-admin")
    public String societyQueue(Model model) {
        var today = time.today();
        model.addAttribute("pending", bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.SOCIETY_ROOM, BookingStatus.PENDING));
        model.addAttribute("active", bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.SOCIETY_ROOM, BookingStatus.APPROVED)
                .stream().filter(b -> !b.getEndDate().isBefore(today)).toList());
        model.addAttribute("freed", bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.SOCIETY_ROOM, BookingStatus.CANCELLED)
                .stream().filter(b -> b.getDecidedAt() != null && !b.getEndDate().isBefore(today)).toList());
        return "admin/society-queue";
    }

    @GetMapping("/society-admin/societies")
    public String societiesPage(Model model) {
        List<Society> all = societyRepo.findAllByOrderByNameAsc();
        model.addAttribute("societies", all);
        model.addAttribute("bearers", bearers.findAll());
        return "admin/societies";
    }

    @PostMapping("/society-admin/societies")
    public String create(@RequestParam String name, @RequestParam(required = false) String description, RedirectAttributes ra) {
        return Flash.run(ra, "/society-admin/societies", () -> societies.create(name, description), "Society registered.");
    }

    @PostMapping("/society-admin/societies/{id}/active")
    public String active(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes ra) {
        Society s = societyRepo.findById(id).orElseThrow(() -> new NotFoundException("Society"));
        return Flash.run(ra, "/society-admin/societies", () -> societies.setActive(s, active), active ? "Reactivated." : "Deactivated.");
    }

    @PostMapping("/society-admin/societies/{id}/bearers")
    public String addBearer(@PathVariable Long id, @RequestParam String email, @RequestParam String position, RedirectAttributes ra) {
        Society s = societyRepo.findById(id).orElseThrow(() -> new NotFoundException("Society"));
        return Flash.run(ra, "/society-admin/societies", () -> societies.addBearer(s, email, position), "Office-bearer added.");
    }

    @PostMapping("/society-admin/bearers/{id}/remove")
    public String removeBearer(@PathVariable Long id, RedirectAttributes ra) {
        SocietyOfficeBearer b = bearers.findById(id).orElseThrow(() -> new NotFoundException("Office-bearer"));
        return Flash.run(ra, "/society-admin/societies", () -> societies.removeBearer(b), "Removed.");
    }
}
