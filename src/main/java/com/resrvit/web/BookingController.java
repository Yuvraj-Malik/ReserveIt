package com.resrvit.web;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Requests (all four kinds), the booking page, decisions on it, offers and QR codes. */
@Controller
@RequiredArgsConstructor
public class BookingController {

    private final CurrentUser currentUser;
    private final BookingService bookingService;
    private final ApprovalService approvals;
    private final BookingRepository bookings;
    private final ApprovalRepository approvalLog;
    private final QueueAuditRepository queueAudit;
    private final SlotOfferRepository offers;
    private final LabRepository labs;
    private final RoomRepository rooms;
    private final EquipmentRepository equipment;
    private final EvaluationService evaluations;
    private final SocietyService societies;
    private final SocietyOfficeBearerRepository bearers;
    private final UserRepository users;
    private final CheckoutRepository checkouts;
    private final GroupService groupService;
    private final QrService qr;
    private final SettingsService settings;
    private final TimeService time;
    private final ChatService chat;

    /** The student's one optional note, posted to the booking's chat (which opens on approval). */
    private void note(Booking b, String note) {
        if (note != null && !note.isBlank()) chat.postRequestNote(b, b.getRequester(), note);
    }

    // ------------------------------------------------------------------ lists

    @GetMapping("/bookings")
    public String mine(Model model) {
        User me = currentUser.get();
        List<Booking> list = new ArrayList<>(bookings.findByRequesterOrderByCreatedAtDesc(me));
        List<StudyGroup> gs = bookingService.activeGroups(me);
        if (!gs.isEmpty()) {
            for (Booking b : bookings.findByGroups(gs)) {
                if (list.stream().noneMatch(x -> x.getId().equals(b.getId()))) list.add(b);
            }
        }
        list.sort(Comparator.comparing(Booking::getCreatedAt).reversed());
        model.addAttribute("list", list);
        model.addAttribute("offers", offers.findOpenForUser(me));
        return "bookings/list";
    }

    // ------------------------------------------------------------------ new requests

    @GetMapping("/bookings/new/equipment")
    public String equipmentForm(Model model) {
        User me = currentUser.get();
        model.addAttribute("labs", labs.findAllByOrderByNameAsc());
        model.addAttribute("reasons", BookingReason.values());
        model.addAttribute("evaluations", evaluations.upcoming());
        model.addAttribute("myGroups", bookingService.activeGroups(me).stream().filter(g -> g.getLeader().sameAs(me)).toList());
        model.addAttribute("leadDays", settings.getInt(SettingsService.LEAD_DAYS, 7));
        return "bookings/new-equipment";
    }

    @PostMapping("/bookings/new/equipment")
    public String requestEquipment(@RequestParam Long labId, @RequestParam Long typeId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(required = false) BookingReason reason, @RequestParam(required = false) String reasonText,
                                   @RequestParam(required = false) Long evaluationId, @RequestParam(required = false) Long groupId,
                                   @RequestParam(defaultValue = "false") boolean urgent,
                                   @RequestParam(required = false) String note, RedirectAttributes ra) {
        try {
            Booking b = bookingService.requestEquipment(currentUser.get(),
                    new BookingService.EquipmentRequest(labId, typeId, date, reason, reasonText, evaluationId, groupId, urgent));
            note(b, note);
            ra.addFlashAttribute("success", b.getStatus() == BookingStatus.APPROVED
                    ? "Booked directly (you're the lab in-charge)." : "Request sent. You're in the queue; you'll be notified when it's decided.");
            return "redirect:/bookings/" + b.getId();
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/bookings/new/equipment";
        }
    }

    @GetMapping("/bookings/new/room")
    public String roomForm(@RequestParam(defaultValue = "lab") String kind, Model model) {
        User me = currentUser.get();
        boolean gd = "gd".equals(kind);
        model.addAttribute("gd", gd);
        model.addAttribute("rooms", gd ? rooms.findByTypeAndBookableTrueOrderByNameAsc(RoomType.GD_ROOM)
                : rooms.findByTypeInAndBookableTrueOrderByNameAsc(List.of(RoomType.EVALUATION_LAB, RoomType.SEMINAR_ROOM)));
        model.addAttribute("gdSlots", CalendarService.GD_SLOTS);
        model.addAttribute("myGroups", bookingService.activeGroups(me).stream().filter(g -> g.getLeader().sameAs(me)).toList());
        model.addAttribute("maxDays", gd ? settings.getInt(SettingsService.GD_DAYS_AHEAD, 3) : settings.getInt(SettingsService.LEAD_DAYS, 7));
        return "bookings/new-room";
    }

    @PostMapping("/bookings/new/room")
    public String requestRoom(@RequestParam(defaultValue = "lab") String kind, @RequestParam Long roomId,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestParam(required = false) String slot,
                              @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime start,
                              @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime end,
                              @RequestParam(required = false) String reasonText, @RequestParam(required = false) Long groupId,
                              @RequestParam(required = false) String note, RedirectAttributes ra) {
        try {
            if (slot != null && !slot.isBlank()) {
                String[] p = slot.split("-");
                start = LocalTime.parse(p[0]);
                end = LocalTime.parse(p[1]);
            }
            BookingService.RoomRequest r = new BookingService.RoomRequest(roomId, date, start, end, reasonText, groupId);
            Booking b = "gd".equals(kind) ? bookingService.requestGdRoom(currentUser.get(), r) : bookingService.requestLabRoom(currentUser.get(), r);
            note(b, note);
            ra.addFlashAttribute("success", b.getStatus() == BookingStatus.APPROVED ? "Booked directly (you're the room's in-charge)." : "Request sent.");
            return "redirect:/bookings/" + b.getId();
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/bookings/new/room?kind=" + ("gd".equals(kind) ? "gd" : "lab");
        }
    }

    @GetMapping("/bookings/new/society")
    public String societyForm(@RequestParam(required = false) Long renew, Model model) {
        User me = currentUser.get();
        model.addAttribute("societies", societies.forBearer(me));
        model.addAttribute("rooms", rooms.findByTypeAndBookableTrueOrderByNameAsc(RoomType.SOCIETY_ROOM));
        if (renew != null) {
            Booking prev = bookings.findById(renew).orElseThrow(() -> new NotFoundException("Booking"));
            model.addAttribute("previous", prev);
            model.addAttribute("history", bookings.findBySocietyOrderBySlotDateDesc(prev.getSociety()));
        }
        return "bookings/new-society";
    }

    @PostMapping("/bookings/new/society")
    public String requestSociety(@RequestParam Long societyId, @RequestParam Long roomId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(required = false) String reasonText,
                                 @RequestParam(required = false) Long previousBookingId,
                                 @RequestParam(required = false) String note, RedirectAttributes ra) {
        try {
            Booking b = bookingService.requestSocietyRoom(currentUser.get(),
                    new BookingService.SocietyRequest(societyId, roomId, from, to, reasonText, previousBookingId));
            note(b, note);
            ra.addFlashAttribute("success", "Request sent to the Society Room Admins.");
            return "redirect:/bookings/" + b.getId();
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/bookings/new/society" + (previousBookingId != null ? "?renew=" + previousBookingId : "");
        }
    }

    // ------------------------------------------------------------------ booking page

    @GetMapping("/bookings/{id}")
    public String show(@PathVariable Long id, Model model) {
        User me = currentUser.get();
        Booking b = load(id);
        if (!canView(b, me)) throw new BookingException("You can't view this booking.");
        model.addAttribute("b", b);
        model.addAttribute("history", approvalLog.findByBookingOrderByCreatedAtAsc(b));
        model.addAttribute("moves", queueAudit.findByBookingOrderByCreatedAtAsc(b));
        model.addAttribute("offers", approvals.openOffers(b));
        model.addAttribute("mode", approvals.modeFor(me, b).name());
        model.addAttribute("canCancel", b.isLive() && b.getStatus() != BookingStatus.CHECKED_OUT && bookingService.canCancel(b, me));
        model.addAttribute("isParty", isParty(b, me));
        model.addAttribute("checkouts", checkouts.findByBookingOrderByIssuedAtAsc(b));
        model.addAttribute("chatOpen", b.getStatus() == BookingStatus.APPROVED || b.getStatus() == BookingStatus.CHECKED_OUT);
        if (b.isPending()) {
            List<Booking> q = approvals.queueOf(b);
            model.addAttribute("queue", q);
            model.addAttribute("position", q.indexOf(b) + 1);
            model.addAttribute("ahead", approvals.aheadOf(b).orElse(null));
            if (b.isForEquipment()) model.addAttribute("fallbackAllowed", approvals.fallbackAllowed(b));
        }
        if (b.getStatus() == BookingStatus.CANCELLED && approvals.modeFor(me, b) != ApprovalService.Mode.NONE) {
            model.addAttribute("reassignCandidates", reassignCandidates(b));
        }
        if (b.getKind() == BookingKind.SOCIETY_ROOM) {
            model.addAttribute("societyHistory", bookings.findBySocietyOrderBySlotDateDesc(b.getSociety()));
            model.addAttribute("canRenew", b.getStatus() == BookingStatus.APPROVED && bearers.existsBySocietyAndUser(b.getSociety(), me));
        }
        return "bookings/show";
    }

    @PostMapping("/bookings/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String note, RedirectAttributes ra) {
        return Flash.run(ra, "/bookings/" + id, () -> bookingService.cancel(load(id), currentUser.get(), note), "Cancelled.");
    }

    @PostMapping("/bookings/{id}/claim")
    public String claim(@PathVariable Long id, @RequestHeader(value = "Referer", required = false) String ref, RedirectAttributes ra) {
        return Flash.run(ra, Flash.back(ref, "/bookings/" + id), () -> approvals.claim(load(id), currentUser.get()), "Claimed. It's locked to you until you decide or release it.");
    }

    @PostMapping("/bookings/{id}/release")
    public String release(@PathVariable Long id, @RequestHeader(value = "Referer", required = false) String ref, RedirectAttributes ra) {
        return Flash.run(ra, Flash.back(ref, "/bookings/" + id), () -> approvals.release(load(id), currentUser.get()), "Released back to the shared queue.");
    }

    @PostMapping("/bookings/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) String note,
                          @RequestHeader(value = "Referer", required = false) String ref, RedirectAttributes ra) {
        return Flash.run(ra, Flash.back(ref, "/bookings/" + id), () -> approvals.approve(load(id), currentUser.get(), note), "Approved #" + id + ".");
    }

    @PostMapping("/bookings/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String note,
                         @RequestHeader(value = "Referer", required = false) String ref, RedirectAttributes ra) {
        return Flash.run(ra, Flash.back(ref, "/bookings/" + id), () -> approvals.reject(load(id), currentUser.get(), note), "Rejected #" + id + ".");
    }

    @PostMapping("/bookings/{id}/emergency")
    public String emergency(@PathVariable Long id, @RequestParam(required = false) String reason,
                            @RequestHeader(value = "Referer", required = false) String ref, RedirectAttributes ra) {
        return Flash.run(ra, Flash.back(ref, "/bookings/" + id), () -> approvals.emergencyApprove(load(id), currentUser.get(), reason),
                "Emergency approval recorded; the in-charge has been told.");
    }

    @PostMapping("/bookings/{id}/offer")
    public String offer(@PathVariable Long id, @RequestHeader(value = "Referer", required = false) String ref, RedirectAttributes ra) {
        User me = currentUser.get();
        return Flash.call(ra, Flash.back(ref, "/bookings/" + id), () -> {
            Booking b = load(id);
            if (approvals.modeFor(me, b) == ApprovalService.Mode.NONE) throw new BookingException("You can't offer alternatives for this request.");
            int n = approvals.offerAlternatives(b, me);
            return n == 0 ? "Nothing free within 7 days; the request stays first in its queue." : "Offered " + n + " alternative day(s).";
        });
    }

    @PostMapping("/bookings/{id}/reassign")
    public String reassign(@PathVariable Long id, @RequestParam Long targetId, RedirectAttributes ra) {
        return Flash.run(ra, "/bookings/" + targetId, () -> approvals.reassign(load(id), load(targetId), currentUser.get()),
                "Slot reassigned and approved.");
    }

    @PostMapping("/offers/{id}/accept")
    public String acceptOffer(@PathVariable Long id, RedirectAttributes ra) {
        SlotOffer o = offers.findById(id).orElseThrow(() -> new NotFoundException("Offer"));
        return Flash.call(ra, "/bookings/" + o.getBooking().getId(), () -> {
            Booking b = approvals.acceptOffer(o, currentUser.get(), false);
            return b.getStatus() == BookingStatus.APPROVED ? "Moved and confirmed for " + b.getWhenLabel() + "."
                    : "That day filled up meanwhile; you've been sent new options.";
        });
    }

    @GetMapping(value = "/bookings/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> qrPng(@PathVariable Long id) {
        User me = currentUser.get();
        Booking b = load(id);
        if (b.getQrToken() == null || !(isParty(b, me) || approvals.modeFor(me, b) != ApprovalService.Mode.NONE)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(qr.png(b.getQrToken(), 320));
    }

    // ------------------------------------------------------------------ helpers

    private Booking load(Long id) {
        return bookings.findById(id).orElseThrow(() -> new NotFoundException("Booking #" + id));
    }

    private boolean isParty(Booking b, User u) {
        if (b.getRequester().sameAs(u)) return true;
        if (b.getGroup() != null && b.getGroup().hasMember(u)) return true;
        return b.getSociety() != null && bearers.existsBySocietyAndUser(b.getSociety(), u);
    }

    private boolean canView(Booking b, User u) {
        if (isParty(b, u)) return true;
        if (approvals.modeFor(u, b) != ApprovalService.Mode.NONE) return true;
        return switch (u.getRole()) {
            case SUPER_ADMIN, FACULTY -> true;
            case LAB_ASSISTANT -> b.getLab() != null && u.getLab() != null && u.getLab().sameAs(b.getLab());
            case GD_ADMIN -> b.getKind() == BookingKind.GD_ROOM;
            case SOCIETY_ADMIN -> b.getKind() == BookingKind.SOCIETY_ROOM;
            default -> false;
        };
    }

    private List<Booking> reassignCandidates(Booking freed) {
        List<Booking> pending = bookings.findByKindAndStatusOrderByQueueRankAsc(freed.getKind(), BookingStatus.PENDING);
        return pending.stream().filter(p -> switch (freed.getKind()) {
            case EQUIPMENT -> p.getLab().sameAs(freed.getLab()) && p.getEquipmentType().sameAs(freed.getEquipmentType());
            case GD_ROOM -> true;
            case LAB_ROOM -> p.getRoom().sameAs(freed.getRoom());
            case SOCIETY_ROOM -> p.getRoom().sameAs(freed.getRoom());
        }).toList();
    }
}
