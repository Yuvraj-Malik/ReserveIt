package com.resrvit.web;

import com.resrvit.domain.*;
import com.resrvit.repo.ConversationRepository;
import com.resrvit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Two-pane chat: the list on the left, the open conversation on the right. */
@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final CurrentUser currentUser;
    private final ChatService chat;
    private final ConversationRepository conversations;
    private final com.resrvit.repo.BookingRepository bookings;

    @GetMapping
    public String inbox(Model model) {
        User me = currentUser.get();
        model.addAttribute("threads", chat.inbox(me));
        model.addAttribute("staff", me.isStaff() ? chat.staffDirectory(me) : java.util.List.of());
        return "chat/inbox";
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        User me = currentUser.get();
        Conversation c = load(id);
        if (!chat.canView(c, me)) throw new BookingException("You can't open this chat.");
        chat.markRead(c, me);
        model.addAttribute("threads", chat.inbox(me));
        model.addAttribute("staff", me.isStaff() ? chat.staffDirectory(me) : java.util.List.of());
        model.addAttribute("c", c);
        model.addAttribute("head", chat.view(c, me));
        model.addAttribute("messages", chat.messages(c));
        model.addAttribute("lockReason", chat.lockReason(c));
        return "chat/inbox";
    }

    @PostMapping("/direct")
    public String direct(@RequestParam(required = false) Long userId, RedirectAttributes ra) {
        try {
            return "redirect:/chat/" + chat.direct(currentUser.get(), userId).getId();
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/chat";
        }
    }

    /** Booking page shortcut: open (or create) that booking's thread. */
    @PostMapping("/booking/{bookingId}")
    public String forBooking(@PathVariable Long bookingId) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> new NotFoundException("Booking"));
        Conversation c = chat.bookingThread(b);
        if (!chat.canView(c, currentUser.get())) throw new BookingException("You can't open this chat.");
        return "redirect:/chat/" + c.getId();
    }

    /** Posted with fetch(); the message reaches everyone (sender included) over STOMP. */
    @PostMapping("/{id}/messages")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> send(@PathVariable Long id, @RequestParam String text) {
        try {
            Message m = chat.send(load(id), currentUser.get(), text);
            return ResponseEntity.ok(Map.of("id", m.getId()));
        } catch (BookingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/read")
    @ResponseBody
    public Map<String, Object> read(@PathVariable Long id) {
        User me = currentUser.get();
        Conversation c = load(id);
        if (chat.canView(c, me)) chat.markRead(c, me);
        return Map.of("ok", true);
    }

    private Conversation load(Long id) {
        return conversations.findById(id).orElseThrow(() -> new NotFoundException("Chat"));
    }
}
