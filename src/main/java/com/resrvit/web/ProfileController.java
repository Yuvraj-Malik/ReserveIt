package com.resrvit.web;

import com.resrvit.domain.Role;
import com.resrvit.domain.User;
import com.resrvit.repo.DepartmentRepository;
import com.resrvit.repo.StrikeRepository;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

/** Settings for every user: profile, photo, security, notifications, appearance, availability, and (super admin) system rules. */
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final CurrentUser currentUser;
    private final AccountService accounts;
    private final ProfileService profiles;
    private final SettingsService settings;
    private final DepartmentRepository departments;
    private final StrikeRepository strikes;

    @GetMapping("/settings")
    public String settingsPage(Model model) {
        User me = currentUser.get();
        model.addAttribute("departments", departments.findAllByOrderByNameAsc());
        model.addAttribute("strikes", strikes.findByUserOrderByCreatedAtDesc(me));
        if (me.getRole() == Role.SUPER_ADMIN) model.addAttribute("system", settings.all());
        return "settings";
    }

    @GetMapping({"/profile", "/strikes"})
    public String legacy() {
        return "redirect:/settings";
    }

    @PostMapping("/settings/profile")
    public String profile(@RequestParam String fullName, @RequestParam(required = false) String phone,
                          @RequestParam(required = false) Long departmentId, RedirectAttributes ra) {
        return Flash.run(ra, "/settings#profile", () -> profiles.updateProfile(currentUser.get(), fullName, phone, departmentId), "Profile saved.");
    }

    @PostMapping("/settings/avatar")
    public String avatar(@RequestParam("photo") MultipartFile photo, RedirectAttributes ra) throws IOException {
        byte[] bytes = photo.getBytes();
        return Flash.run(ra, "/settings#profile", () -> profiles.setAvatar(currentUser.get(), bytes), "Photo updated.");
    }

    @PostMapping("/settings/avatar/remove")
    public String removeAvatar(RedirectAttributes ra) {
        return Flash.run(ra, "/settings#profile", () -> profiles.removeAvatar(currentUser.get()), "Photo removed.");
    }

    @PostMapping("/settings/password")
    public String password(@RequestParam String current, @RequestParam String next, @RequestParam String confirm, RedirectAttributes ra) {
        return Flash.run(ra, "/settings#security", () -> {
            if (!next.equals(confirm)) throw new BookingException("The new passwords don't match.");
            accounts.changePassword(currentUser.get(), current, next);
        }, "Password changed.");
    }

    @PostMapping("/settings/preferences")
    public String preferences(@RequestParam(defaultValue = "false") boolean emailNotifications,
                              @RequestParam(defaultValue = "light") String theme, RedirectAttributes ra) {
        return Flash.run(ra, "/settings#preferences", () -> profiles.updatePreferences(currentUser.get(), emailNotifications, theme), "Preferences saved.");
    }

    @PostMapping("/settings/leave")
    public String leave(@RequestParam boolean onLeave, RedirectAttributes ra) {
        User me = currentUser.get();
        return Flash.run(ra, "/settings#availability", () -> {
            if (me.getRole() != Role.FACULTY) throw new BookingException("Only faculty can mark leave.");
            accounts.setOnLeave(me, onLeave);
        }, onLeave ? "Marked on leave: lab assistants can approve your labs' equipment requests." : "Welcome back: you approve your labs' requests again.");
    }

    @PostMapping("/settings/system")
    public String system(@RequestParam String key, @RequestParam String value, RedirectAttributes ra) {
        return Flash.run(ra, "/settings#system", () -> {
            if (currentUser.get().getRole() != Role.SUPER_ADMIN) throw new BookingException("Only the super admin changes system settings.");
            if (value == null || value.isBlank()) throw new BookingException("Enter a value.");
            settings.set(key, value);
        }, "Setting saved.");
    }
}
