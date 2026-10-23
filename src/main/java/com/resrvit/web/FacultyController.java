package com.resrvit.web;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/** Faculty in-charge approvals and evaluation schedules. */
@Controller
@RequestMapping("/faculty")
@RequiredArgsConstructor
public class FacultyController {

    private final CurrentUser currentUser;
    private final LabRepository labs;
    private final RoomRepository rooms;
    private final EquipmentTypeRepository types;
    private final EvaluationScheduleRepository evaluationRepo;
    private final EvaluationService evaluations;
    private final ApprovalService approvals;

    @GetMapping("/approvals")
    public String approvalsPage(Model model) {
        User me = currentUser.get();
        List<Lab> myLabs = me.getRole() == Role.SUPER_ADMIN ? labs.findAllByOrderByNameAsc() : labs.findByInCharge(me);
        List<Room> myRooms = me.getRole() == Role.SUPER_ADMIN
                ? rooms.findByTypeInAndBookableTrueOrderByNameAsc(List.of(RoomType.EVALUATION_LAB, RoomType.SEMINAR_ROOM))
                : rooms.findApprovableBy(me, List.of(RoomType.EVALUATION_LAB, RoomType.SEMINAR_ROOM));
        List<Booking> pending = approvals.pendingFor(me, myLabs, myRooms);
        // Group into queues so the first in line is obvious.
        Map<String, List<Booking>> queues = new LinkedHashMap<>();
        for (Booking b : pending) queues.computeIfAbsent(b.getQueueKey(), k -> new ArrayList<>()).add(b);
        queues.values().forEach(l -> l.sort(Comparator.comparingLong(Booking::getQueueRank)));
        Map<Long, Boolean> hasOffers = new HashMap<>();
        pending.forEach(b -> hasOffers.put(b.getId(), approvals.hasOpenOffers(b)));
        model.addAttribute("queues", queues);
        model.addAttribute("hasOffers", hasOffers);
        model.addAttribute("myLabs", myLabs);
        model.addAttribute("myRooms", myRooms);
        return "faculty/approvals";
    }

    @GetMapping("/evaluations")
    public String evaluationsPage(Model model) {
        User me = currentUser.get();
        model.addAttribute("mine", evaluationRepo.findByFacultyOrderByEvalDateDesc(me));
        model.addAttribute("labs", labs.findAllByOrderByNameAsc());
        model.addAttribute("types", types.findAllByOrderByNameAsc());
        return "faculty/evaluations";
    }

    @PostMapping("/evaluations")
    public String addEvaluation(@RequestParam Long labId, @RequestParam(required = false) Long typeId, @RequestParam String courseCode,
                                @RequestParam String title, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate evalDate,
                                RedirectAttributes ra) {
        return Flash.call(ra, "/faculty/evaluations", () -> {
            EvaluationService.Created c = evaluations.create(currentUser.get(), labId, typeId, courseCode, title, evalDate);
            return "Evaluation added." + (c.upgraded() > 0 ? " " + c.upgraded() + " existing booking(s) were upgraded to protected." : "");
        });
    }
}
