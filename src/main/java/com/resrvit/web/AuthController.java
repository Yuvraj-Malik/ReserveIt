package com.resrvit.web;

import com.resrvit.domain.Role;
import com.resrvit.repo.DepartmentRepository;
import com.resrvit.service.AccountService;
import com.resrvit.service.BookingException;
import com.resrvit.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accounts;
    private final DepartmentRepository departments;
    private final SettingsService settings;

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("domain", settings.emailDomain());
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("departments", departments.findAllByOrderByNameAsc());
        model.addAttribute("domain", settings.emailDomain());
        model.addAttribute("roles", List.of(Role.STUDENT, Role.FACULTY, Role.LAB_ASSISTANT));
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@RequestParam String email, @RequestParam String fullName, @RequestParam(required = false) String rollNo,
                         @RequestParam(required = false) Long departmentId, @RequestParam Role role,
                         @RequestParam String password, @RequestParam String confirm, Model model, RedirectAttributes ra) {
        try {
            if (!password.equals(confirm)) throw new BookingException("The passwords don't match.");
            AccountService.SignupResult r = accounts.signup(email, fullName, rollNo, departmentId, role, password);
            ra.addAttribute("email", r.user().getEmail());
            if (r.devOtp() != null) ra.addFlashAttribute("devOtp", r.devOtp());
            ra.addFlashAttribute("success", "We sent a 6-digit code to " + r.user().getEmail() + ".");
            return "redirect:/verify";
        } catch (BookingException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("form", new SignupForm(email, fullName, rollNo, departmentId, role));
            return signupForm(model);
        }
    }

    public record SignupForm(String email, String fullName, String rollNo, Long departmentId, Role role) {}

    @GetMapping("/verify")
    public String verifyForm(@RequestParam(required = false) String email, Model model) {
        model.addAttribute("email", email);
        return "auth/verify";
    }

    @PostMapping("/verify")
    public String verify(@RequestParam String email, @RequestParam String code, RedirectAttributes ra) {
        try {
            accounts.verify(email, code);
            ra.addFlashAttribute("success", "Email verified. An admin will activate your account; you'll get an email when it's ready.");
            return "redirect:/login";
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addAttribute("email", email);
            return "redirect:/verify";
        }
    }

    @PostMapping("/verify/resend")
    public String resend(@RequestParam String email, RedirectAttributes ra) {
        try {
            String otp = accounts.resendOtp(email);
            if (otp != null) ra.addFlashAttribute("devOtp", otp);
            ra.addFlashAttribute("success", "A new code is on its way.");
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        ra.addAttribute("email", email);
        return "redirect:/verify";
    }
}
