package com.resrvit.web;

import com.resrvit.domain.Notification;
import com.resrvit.domain.User;
import com.resrvit.service.ChatService;
import com.resrvit.service.CurrentUser;
import com.resrvit.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The bell panel's JSON API, the full notifications page, and live counters. */
@Controller
@RequiredArgsConstructor
public class NotificationController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("d MMM, h:mm a");

    private final CurrentUser currentUser;
    private final NotificationService notifications;
    private final ChatService chat;

    @GetMapping("/notifications")
    public String page(Model model) {
        model.addAttribute("items", notifications.recent(currentUser.get()));
        return "notifications";
    }

    /** Clicking a notification marks it read, then goes to what it is about. */
    @GetMapping("/notifications/{id}/open")
    public String open(@PathVariable Long id) {
        Notification n = notifications.markRead(currentUser.get(), id);
        String link = n.getLink();
        return "redirect:" + (link != null && link.startsWith("/") && !link.startsWith("//") ? link : "/notifications");
    }

    @GetMapping("/api/notifications")
    @ResponseBody
    public Map<String, Object> list() {
        User me = currentUser.get();
        List<Map<String, Object>> items = notifications.recent(me).stream().limit(40).map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("message", n.getMessage());
            m.put("link", n.getLink());
            m.put("read", n.isRead());
            m.put("at", TS.format(n.getCreatedAt()));
            return m;
        }).toList();
        return Map.of("items", items, "unread", notifications.unread(me));
    }

    @GetMapping("/api/counters")
    @ResponseBody
    public Map<String, Object> counters() {
        User me = currentUser.get();
        return Map.of("notifications", notifications.unread(me), "chat", chat.totalUnread(me));
    }

    @PostMapping("/api/notifications/{id}/read")
    @ResponseBody
    public Map<String, Object> markRead(@PathVariable Long id) {
        User me = currentUser.get();
        notifications.markRead(me, id);
        return Map.of("unread", notifications.unread(me));
    }

    @PostMapping("/api/notifications/{id}/delete")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable Long id) {
        User me = currentUser.get();
        notifications.delete(me, id);
        return Map.of("unread", notifications.unread(me));
    }

    @PostMapping("/api/notifications/read-all")
    @ResponseBody
    public Map<String, Object> readAll() {
        notifications.markAllRead(currentUser.get());
        return Map.of("unread", 0);
    }

    @PostMapping("/api/notifications/delete-all")
    @ResponseBody
    public Map<String, Object> deleteAll() {
        notifications.deleteAll(currentUser.get());
        return Map.of("unread", 0);
    }
}
