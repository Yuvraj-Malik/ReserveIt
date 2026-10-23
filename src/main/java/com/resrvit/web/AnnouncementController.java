package com.resrvit.web;

import com.resrvit.domain.Role;
import com.resrvit.repo.SocietyRepository;
import com.resrvit.repo.StudyGroupRepository;
import com.resrvit.repo.UserRepository;
import com.resrvit.service.AnnouncementService;
import com.resrvit.service.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final CurrentUser currentUser;
    private final AnnouncementService announcements;
    private final UserRepository users;
    private final StudyGroupRepository groups;
    private final SocietyRepository societies;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("audiences", AnnouncementService.Audience.values());
        model.addAttribute("roles", Role.values());
        model.addAttribute("people", users.findAllByOrderByRoleAscFullNameAsc().stream().filter(u -> u.isActive()).toList());
        model.addAttribute("groups", groups.findAllByOrderByCreatedAtDesc().stream()
                .filter(g -> g.getStatus() == com.resrvit.domain.GroupStatus.ACTIVE).toList());
        model.addAttribute("societies", societies.findByActiveTrueOrderByNameAsc());
        model.addAttribute("history", announcements.history(currentUser.get()));
        return "announcements";
    }

    @PostMapping
    public String send(@RequestParam AnnouncementService.Audience audience, @RequestParam(required = false) String role,
                       @RequestParam(required = false) String person, @RequestParam(required = false) String group,
                       @RequestParam(required = false) String society, @RequestParam String message,
                       @RequestParam(defaultValue = "false") boolean email, RedirectAttributes ra) {
        String target = switch (audience) {
            case ROLE -> role;
            case PERSON -> person;
            case GROUP -> group;
            case SOCIETY -> society;
            default -> null;
        };
        return Flash.call(ra, "/announcements", () -> {
            var a = announcements.send(currentUser.get(), audience, target, message, email);
            return "Sent to " + a.getRecipientCount() + " " + (a.getRecipientCount() == 1 ? "person" : "people") + " (" + a.getAudience() + ").";
        });
    }
}
