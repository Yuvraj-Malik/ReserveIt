package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.repo.NotificationRepository;
import com.resrvit.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * In-app notifications for every status change and reminder, emailed too unless the user turned
 * email off in Settings. Open pages are told to refresh their counter over STOMP.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    private final UserRepository users;
    private final MailService mail;
    private final TimeService time;
    private final QueueEvents events;

    @Value("${resrvit.base-url}")
    private String baseUrl;

    @Transactional
    public void notify(User user, String message, String link) {
        save(user, message, link, true);
    }

    private void save(User user, String message, String link, boolean email) {
        if (user == null || user.getStatus() == UserStatus.DEACTIVATED) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(message.length() > 490 ? message.substring(0, 490) + "…" : message);
        n.setLink(link);
        n.setCreatedAt(time.now());
        repo.save(n);
        events.userChanged(user.getId(), "notifications");
        if (email && user.isEmailNotifications()) {
            String body = message + (link != null ? "\n\nOpen: " + baseUrl + link : "") + "\n\n— ResrvIt";
            mail.send(user.getEmail(), firstWords(message), body);
        }
    }

    public void notifyAll(Collection<User> recipients, String message, String link) {
        Set<Long> seen = new LinkedHashSet<>();
        for (User u : recipients) {
            if (u != null && seen.add(u.getId())) notify(u, message, link);
        }
    }

    /** In-app only (no email): used for chat messages so inboxes aren't flooded. */
    @Transactional
    public void notifyAllQuietly(Collection<User> recipients, String message, String link) {
        Set<Long> seen = new LinkedHashSet<>();
        for (User u : recipients) {
            if (u != null && seen.add(u.getId())) save(u, message, link, false);
        }
    }

    public void notifyRole(Role role, String message, String link) {
        notifyAll(users.findByRoleAndStatus(role, UserStatus.ACTIVE), message, link);
    }

    /** The person who made the request, plus every group member it covers. */
    public void notifyBookingParties(Booking b, String message) {
        List<User> list = new ArrayList<>();
        list.add(b.getRequester());
        if (b.getGroup() != null) b.getGroup().getMembers().forEach(m -> list.add(m.getUser()));
        notifyAll(list, message, "/bookings/" + b.getId());
    }

    public List<Notification> recent(User u) {
        return repo.findTop100ByUserOrderByCreatedAtDesc(u);
    }

    public long unread(User u) {
        return repo.countByUserAndReadFalse(u);
    }

    @Transactional
    public void markAllRead(User u) {
        repo.markAllRead(u);
        events.userChanged(u.getId(), "notifications");
    }

    @Transactional
    public void deleteAll(User u) {
        repo.deleteAllForUser(u);
        events.userChanged(u.getId(), "notifications");
    }

    @Transactional
    public Notification markRead(User u, Long id) {
        Notification n = own(u, id);
        n.setRead(true);
        repo.save(n);
        return n;
    }

    @Transactional
    public void delete(User u, Long id) {
        repo.delete(own(u, id));
    }

    private Notification own(User u, Long id) {
        Notification n = repo.findById(id).orElseThrow(() -> new BookingException("Notification not found."));
        if (!n.getUser().sameAs(u)) throw new BookingException("Notification not found.");
        return n;
    }

    private static String firstWords(String s) {
        String t = s.length() > 70 ? s.substring(0, 70) + "…" : s;
        return t.replace('\n', ' ');
    }
}
