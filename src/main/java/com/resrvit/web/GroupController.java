package com.resrvit.web;

import com.resrvit.domain.*;
import com.resrvit.repo.BookingRepository;
import com.resrvit.repo.StudyGroupRepository;
import com.resrvit.repo.UserRepository;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    private final CurrentUser currentUser;
    private final StudyGroupRepository groups;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final GroupService groupService;

    @GetMapping
    public String list(Model model) {
        User me = currentUser.get();
        List<StudyGroup> list = switch (me.getRole()) {
            case STUDENT -> groups.findForMember(me);
            case FACULTY, SUPER_ADMIN, LAB_ASSISTANT -> groups.findAllByOrderByCreatedAtDesc();
            default -> List.of();
        };
        model.addAttribute("groups", list);
        return "groups/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("students", users.activeStudents());
        model.addAttribute("faculty", users.activeFaculty());
        return "groups/new";
    }

    @PostMapping
    public String create(@RequestParam String name, @RequestParam String courseCode, @RequestParam(required = false) String projectTitle,
                         @RequestParam(required = false) List<Long> memberIds, @RequestParam(required = false) Long leaderId,
                         @RequestParam(required = false) Long facultyId, RedirectAttributes ra) {
        User me = currentUser.get();
        try {
            StudyGroup g = me.getRole() == Role.STUDENT
                    ? groupService.createByStudent(me, name, courseCode, projectTitle, memberIds, facultyId)
                    : groupService.createByFaculty(me, name, courseCode, projectTitle, memberIds, leaderId);
            ra.addFlashAttribute("success", g.getStatus() == GroupStatus.ACTIVE ? "Group created and active."
                    : "Group created. Members must confirm, then the course faculty approves it.");
            return "redirect:/groups/" + g.getId();
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/groups/new";
        }
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        User me = currentUser.get();
        StudyGroup g = load(id);
        if (!groupService.canView(g, me)) throw new BookingException("You can't view this group.");
        model.addAttribute("g", g);
        model.addAttribute("locked", groupService.isLocked(g));
        model.addAttribute("isLeader", g.getLeader().sameAs(me));
        model.addAttribute("isMember", g.hasMember(me));
        model.addAttribute("myRow", g.getMembers().stream().filter(m -> m.getUser().sameAs(me)).findFirst().orElse(null));
        model.addAttribute("canEdit", me.getRole() == Role.FACULTY || me.getRole() == Role.SUPER_ADMIN);
        model.addAttribute("canDecide", g.getStatus() == GroupStatus.PENDING_FACULTY && (groupService.isCourseFaculty(g, me) || me.getRole() == Role.SUPER_ADMIN));
        model.addAttribute("bookings", bookings.findByGroups(List.of(g)));
        model.addAttribute("students", users.activeStudents());
        return "groups/show";
    }

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes ra) {
        return Flash.run(ra, "/groups/" + id, () -> groupService.confirm(load(id), currentUser.get()), "Confirmed.");
    }

    @PostMapping("/{id}/decline")
    public String decline(@PathVariable Long id, RedirectAttributes ra) {
        return Flash.run(ra, "/groups", () -> groupService.decline(load(id), currentUser.get()), "Declined.");
    }

    @PostMapping("/{id}/decision")
    public String decide(@PathVariable Long id, @RequestParam boolean approve, RedirectAttributes ra) {
        return Flash.run(ra, "/groups/" + id, () -> groupService.facultyDecision(load(id), currentUser.get(), approve), approve ? "Group approved." : "Group not approved.");
    }

    @PostMapping("/{id}/members")
    public String addMember(@PathVariable Long id, @RequestParam Long userId, RedirectAttributes ra) {
        return Flash.run(ra, "/groups/" + id, () -> groupService.addMember(load(id), currentUser.get(), userId), "Member added.");
    }

    @PostMapping("/{id}/members/{userId}/remove")
    public String removeMember(@PathVariable Long id, @PathVariable Long userId, RedirectAttributes ra) {
        return Flash.run(ra, "/groups/" + id, () -> groupService.removeMember(load(id), currentUser.get(), userId), "Member removed.");
    }

    @PostMapping("/{id}/leader")
    public String leader(@PathVariable Long id, @RequestParam Long userId, RedirectAttributes ra) {
        return Flash.run(ra, "/groups/" + id, () -> groupService.transferLeadership(load(id), currentUser.get(), userId), "Leadership transferred.");
    }

    @PostMapping("/{id}/dissolve")
    public String dissolve(@PathVariable Long id, RedirectAttributes ra) {
        return Flash.run(ra, "/groups/" + id, () -> groupService.dissolve(load(id), currentUser.get()), "Group dissolved.");
    }

    private StudyGroup load(Long id) {
        return groups.findById(id).orElseThrow(() -> new NotFoundException("Group"));
    }
}
