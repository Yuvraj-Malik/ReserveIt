package com.resrvit.web;

import com.resrvit.domain.User;
import com.resrvit.service.BookingException;
import com.resrvit.service.CurrentUser;
import com.resrvit.service.NotificationService;
import com.resrvit.service.TimeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@ControllerAdvice(basePackages = "com.resrvit.web")
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final CurrentUser currentUser;
    private final NotificationService notifications;
    private final TimeService time;
    private final com.resrvit.service.ChatService chat;
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> google;

    @ModelAttribute("chatUnread")
    public long chatUnread() {
        return currentUser.find().map(chat::totalUnread).orElse(0L);
    }

    @ModelAttribute("googleEnabled")
    public boolean googleEnabled() {
        return google.getIfAvailable() != null;
    }

    @ModelAttribute("me")
    public User me() {
        return currentUser.find().orElse(null);
    }

    @ModelAttribute("unreadCount")
    public long unread() {
        return currentUser.find().map(notifications::unread).orElse(0L);
    }

    @ModelAttribute("today")
    public LocalDate today() {
        return time.today();
    }

    /** A rule broken outside a Flash.run block: go back with the message. */
    @ExceptionHandler(BookingException.class)
    public String onRule(BookingException e, HttpServletRequest req, RedirectAttributes ra) {
        ra.addFlashAttribute("error", e.getMessage());
        return "redirect:" + Flash.back(req.getHeader("Referer"), "/");
    }

    @ExceptionHandler(NotFoundException.class)
    public String onMissing(NotFoundException e, RedirectAttributes ra) {
        ra.addFlashAttribute("error", e.getMessage());
        return "redirect:/";
    }
}
