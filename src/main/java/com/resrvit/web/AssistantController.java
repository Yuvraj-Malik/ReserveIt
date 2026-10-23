package com.resrvit.web;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Lab assistant tools: queue and reorder, QR scan, check-out/in, incidents, emergencies, maintenance, strikes. */
@Controller
@RequestMapping("/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final CurrentUser currentUser;
    private final BookingRepository bookings;
    private final LabRepository labs;
    private final EquipmentRepository equipment;
    private final RoomRepository rooms;
    private final UserRepository users;
    private final IncidentRepository incidents;
    private final StrikeRepository strikeRepo;
    private final MaintenanceLogRepository maintenanceRepo;
    private final CheckoutRepository checkouts;
    private final ApprovalService approvals;
    private final BookingService bookingService;
    private final CheckoutService checkoutService;
    private final StrikeService strikes;
    private final CollisionService collisions;
    private final AvailabilityService availabilityService;
    private final TimeService time;

    private List<Lab> myLabs(User me) {
        if (me.getRole() == Role.SUPER_ADMIN) return labs.findAllByOrderByNameAsc();
        return me.getLab() == null ? List.of() : List.of(me.getLab());
    }

    // ------------------------------------------------------------------ queue

    @GetMapping("/queue")
    public String queue(Model model) {
        User me = currentUser.get();
        List<Lab> mine = myLabs(me);
        List<Booking> pending = mine.isEmpty() ? List.of() : bookings.pendingEquipmentForLabs(mine);
        Map<String, List<Booking>> queues = new LinkedHashMap<>();
        for (Booking b : pending) queues.computeIfAbsent(b.getQueueKey(), k -> new ArrayList<>()).add(b);
        queues.values().forEach(l -> l.sort(Comparator.comparingLong(Booking::getQueueRank)));
        Map<Long, Long> daysToEval = new HashMap<>();
        Map<Long, Boolean> fallback = new HashMap<>();
        Map<Long, Boolean> hasOffers = new HashMap<>();
        Map<String, Integer> free = new HashMap<>();
        for (Booking b : pending) {
            if (b.getEvaluation() != null) daysToEval.put(b.getId(), ChronoUnit.DAYS.between(time.today(), b.getEvaluation().getEvalDate()));
            fallback.put(b.getId(), approvals.fallbackAllowed(b));
            hasOffers.put(b.getId(), approvals.hasOpenOffers(b));
        }
        for (List<Booking> q : queues.values()) {
            Booking f = q.get(0);
            free.put(f.getQueueKey(), availabilityService.free(f.getLab(), f.getEquipmentType(), f.getSlotDate()));
        }
        model.addAttribute("queues", queues);
        model.addAttribute("daysToEval", daysToEval);
        model.addAttribute("fallback", fallback);
        model.addAttribute("hasOffers", hasOffers);
        model.addAttribute("free", free);
        model.addAttribute("labs", mine);
        model.addAttribute("waitlisted", pending.stream().filter(Booking::isWaitlistPriority).toList());
        model.addAttribute("freed", mine.isEmpty() ? List.of() : bookings.findByKindAndStatusOrderByQueueRankAsc(BookingKind.EQUIPMENT, BookingStatus.CANCELLED)
                .stream().filter(b -> b.getEquipment() != null && !b.getSlotDate().isBefore(time.today()) && mine.stream().anyMatch(l -> l.sameAs(b.getLab()))).toList());
        return "assistant/queue";
    }

    public record ReorderRequest(List<Long> order, String reason) {}

    @PostMapping("/queue/reorder")
    @ResponseBody
    public ResponseEntity<Map<String, String>> reorder(@RequestBody ReorderRequest req) {
        try {
            approvals.reorder(currentUser.get(), req.order(), req.reason());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (BookingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ QR check-out / check-in

    @GetMapping("/scan")
    public String scan(Model model) {
        User me = currentUser.get();
        List<Lab> mine = myLabs(me);
        List<Booking> today = new ArrayList<>();
        for (Lab l : mine) today.addAll(bookings.approvedEquipmentForLabOn(l, time.today()));
        model.addAttribute("pickups", today);
        model.addAttribute("out", checkouts.findByReturnedAtIsNullOrderByDueAtAsc().stream()
                .filter(c -> mine.stream().anyMatch(l -> l.sameAs(c.getBooking().getLab()))).toList());
        return "assistant/scan";
    }

    @GetMapping("/checkout")
    public String checkoutPage(@RequestParam String token, Model model, RedirectAttributes ra) {
        try {
            Booking b = checkoutService.byToken(token);
            model.addAttribute("b", b);
            model.addAttribute("token", b.getQrToken());
            model.addAttribute("open", checkouts.findFirstByBookingAndReturnedAtIsNull(b).orElse(null));
            model.addAttribute("now", time.now());
            return "assistant/checkout";
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/assistant/scan";
        }
    }

    @PostMapping("/checkout/issue")
    public String issue(@RequestParam String token, @RequestParam(required = false) String condition, RedirectAttributes ra) {
        return Flash.run(ra, "/assistant/scan", () -> checkoutService.issue(checkoutService.byToken(token), currentUser.get(), condition),
                "Issued. Due back in 24 hours.");
    }

    @PostMapping("/checkout/receive")
    public String receive(@RequestParam String token, @RequestParam(required = false) String condition,
                          @RequestParam(defaultValue = "false") boolean damaged, @RequestParam(required = false) String damage,
                          @RequestParam(defaultValue = "false") boolean maintenance, RedirectAttributes ra) {
        return Flash.call(ra, "/assistant/scan", () -> {
            Checkout c = checkoutService.receive(checkoutService.byToken(token), currentUser.get(), condition, damaged, damage, maintenance);
            return "Returned" + (c.isLate() ? " late — a strike was recorded" : " on time") + (c.isDamaged() ? "; incident filed" : "") + ".";
        });
    }

    // ------------------------------------------------------------------ incidents

    @GetMapping("/incidents")
    public String incidentsPage(Model model) {
        User me = currentUser.get();
        List<Lab> mine = myLabs(me);
        model.addAttribute("items", incidents.findAllByOrderByCreatedAtDesc().stream()
                .filter(i -> mine.stream().anyMatch(l -> l.sameAs(i.getLab()))).toList());
        return "assistant/incidents";
    }

    @PostMapping("/incidents/{id}/resolve")
    public String resolveIncident(@PathVariable Long id, @RequestParam String resolution, RedirectAttributes ra) {
        Incident i = incidents.findById(id).orElseThrow(() -> new NotFoundException("Incident"));
        return Flash.run(ra, "/assistant/incidents", () -> checkoutService.resolveIncident(i, currentUser.get(), resolution), "Resolved; the block is lifted.");
    }

    // ------------------------------------------------------------------ emergency booking

    @GetMapping("/emergency")
    public String emergencyForm(Model model) {
        User me = currentUser.get();
        model.addAttribute("students", users.activeStudents());
        model.addAttribute("types", me.getLab() == null ? List.of() : equipment.typesInLab(me.getLab()));
        return "assistant/emergency";
    }

    @PostMapping("/emergency")
    public String emergency(@RequestParam Long studentId, @RequestParam Long typeId,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestParam String reason, RedirectAttributes ra) {
        try {
            User student = users.findById(studentId).orElseThrow(() -> new BookingException("Pick a student."));
            Booking b = bookingService.emergencyBooking(currentUser.get(), student, typeId, date, reason);
            ra.addFlashAttribute("success", "Emergency booking #" + b.getId() + " approved and logged.");
            return "redirect:/bookings/" + b.getId();
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/assistant/emergency";
        }
    }

    // ------------------------------------------------------------------ maintenance

    @GetMapping("/maintenance")
    public String maintenancePage(Model model) {
        User me = currentUser.get();
        List<Lab> mine = myLabs(me);
        model.addAttribute("logs", maintenanceRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(m -> me.getRole() == Role.SUPER_ADMIN || (m.getEquipment() != null && mine.stream().anyMatch(l -> l.sameAs(m.getEquipment().getLab())))
                        || (m.getRoom() != null && m.getRoom().getLab() != null && mine.stream().anyMatch(l -> l.sameAs(m.getRoom().getLab()))))
                .toList());
        model.addAttribute("items", mine.stream().flatMap(l -> equipment.findByLabOrderByAssetTagAsc(l).stream()).toList());
        model.addAttribute("rooms", me.getRole() == Role.SUPER_ADMIN ? rooms.findAllByOrderByNameAsc()
                : rooms.findAllByOrderByNameAsc().stream().filter(r -> r.getLab() != null && mine.stream().anyMatch(l -> l.sameAs(r.getLab()))).toList());
        return "assistant/maintenance";
    }

    @PostMapping("/maintenance/preview")
    public String maintenancePreview(@RequestParam(required = false) Long equipmentId, @RequestParam(required = false) Long roomId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam String reason, Model model, RedirectAttributes ra) {
        try {
            checkScope(equipmentId, roomId);
            CollisionService.Result r = collisions.applyMaintenance(equipmentId, roomId, from, to, reason, currentUser.get(), true);
            model.addAttribute("result", r);
            model.addAttribute("title", "Maintenance block: impact preview");
            model.addAttribute("confirmAction", "/assistant/maintenance/confirm");
            model.addAttribute("hidden", Map.of("equipmentId", equipmentId == null ? "" : equipmentId.toString(),
                    "roomId", roomId == null ? "" : roomId.toString(), "from", from.toString(), "to", to.toString(), "reason", reason));
            model.addAttribute("back", "/assistant/maintenance");
            return "impact";
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/assistant/maintenance";
        }
    }

    @PostMapping("/maintenance/confirm")
    public String maintenanceConfirm(@RequestParam(required = false) Long equipmentId, @RequestParam(required = false) Long roomId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam String reason, RedirectAttributes ra) {
        return Flash.call(ra, "/assistant/maintenance", () -> {
            checkScope(equipmentId, roomId);
            CollisionService.Result r = collisions.applyMaintenance(equipmentId, roomId, from, to, reason, currentUser.get(), false);
            return "Maintenance block saved. " + r.impacts().size() + " booking(s) adjusted.";
        });
    }

    @PostMapping("/maintenance/{id}/close")
    public String closeMaintenance(@PathVariable Long id, RedirectAttributes ra) {
        MaintenanceLog m = maintenanceRepo.findById(id).orElseThrow(() -> new NotFoundException("Maintenance log"));
        return Flash.run(ra, "/assistant/maintenance", () -> collisions.closeMaintenance(m), "Maintenance closed; the item is bookable again.");
    }

    private void checkScope(Long equipmentId, Long roomId) {
        User me = currentUser.get();
        if (me.getRole() == Role.SUPER_ADMIN) return;
        List<Lab> mine = myLabs(me);
        if (equipmentId != null) {
            Equipment e = equipment.findById(equipmentId).orElseThrow(() -> new BookingException("Unknown item."));
            if (mine.stream().noneMatch(l -> l.sameAs(e.getLab()))) throw new BookingException("That item belongs to another lab.");
        }
        if (roomId != null) {
            Room r = rooms.findById(roomId).orElseThrow(() -> new BookingException("Unknown room."));
            if (r.getLab() == null || mine.stream().noneMatch(l -> l.sameAs(r.getLab()))) throw new BookingException("That room isn't part of your lab.");
        }
    }

    // ------------------------------------------------------------------ strikes

    @GetMapping("/strikes")
    public String strikesPage(Model model) {
        model.addAttribute("items", strikeRepo.findAllByOrderByCreatedAtDesc());
        return "assistant/strikes";
    }

    @PostMapping("/strikes/{id}/waive")
    public String waive(@PathVariable Long id, RedirectAttributes ra) {
        Strike s = strikeRepo.findById(id).orElseThrow(() -> new NotFoundException("Strike"));
        return Flash.run(ra, "/assistant/strikes", () -> strikes.waive(s, currentUser.get()), "Strike waived.");
    }
}
