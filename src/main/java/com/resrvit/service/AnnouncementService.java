package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Admins (and lab assistants) send a notification to everyone, a role, one person, a study group or a society. */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    public enum Audience {
        EVERYONE("Everyone"), ROLE("Everyone with a role"), PERSON("One person"), GROUP("A study group"), SOCIETY("A society's office-bearers");
        private final String label;
        Audience(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final UserRepository users;
    private final StudyGroupRepository groups;
    private final SocietyRepository societies;
    private final SocietyOfficeBearerRepository bearers;
    private final AnnouncementRepository announcements;
    private final NotificationService notifications;
    private final TimeService time;

    @Transactional
    public Announcement send(User sender, Audience audience, String target, String message, boolean email) {
        if (!sender.isAnnouncer()) throw new BookingException("Only admins and lab assistants can send announcements.");
        if (audience == null) throw new BookingException("Pick who should get it.");
        if (message == null || message.isBlank()) throw new BookingException("Write the message.");
        if (message.length() > 900) throw new BookingException("Keep it under 900 characters.");
        String label;
        List<User> to;
        switch (audience) {
            case EVERYONE -> {
                to = users.findAllByOrderByRoleAscFullNameAsc().stream().filter(User::isActive).toList();
                label = "Everyone";
            }
            case ROLE -> {
                Role r;
                try { r = Role.valueOf(target); } catch (Exception e) { throw new BookingException("Pick a role."); }
                to = users.findByRoleAndStatus(r, UserStatus.ACTIVE);
                label = "All " + r.getLabel().toLowerCase() + "s";
            }
            case PERSON -> {
                User u = users.findById(parse(target)).orElseThrow(() -> new BookingException("Pick a person."));
                to = List.of(u);
                label = u.getFullName();
            }
            case GROUP -> {
                StudyGroup g = groups.findById(parse(target)).orElseThrow(() -> new BookingException("Pick a group."));
                to = g.getMembers().stream().map(GroupMember::getUser).toList();
                label = "Group " + g.getName();
            }
            case SOCIETY -> {
                Society s = societies.findById(parse(target)).orElseThrow(() -> new BookingException("Pick a society."));
                to = bearers.findBySociety(s).stream().map(SocietyOfficeBearer::getUser).toList();
                label = s.getName() + " office-bearers";
            }
            default -> throw new BookingException("Pick who should get it.");
        }
        to = to.stream().filter(u -> !u.sameAs(sender)).toList();
        if (to.isEmpty()) throw new BookingException("Nobody matches that audience.");
        String text = "📢 " + sender.getFullName() + ": " + message.trim();
        if (email) notifications.notifyAll(to, text, "/notifications");
        else notifications.notifyAllQuietly(to, text, "/notifications");
        Announcement a = new Announcement();
        a.setSender(sender);
        a.setAudience(label);
        a.setMessage(message.trim());
        a.setRecipientCount(to.size());
        a.setCreatedAt(time.now());
        return announcements.save(a);
    }

    public List<Announcement> history(User u) {
        return u.getRole() == Role.SUPER_ADMIN ? announcements.findTop50ByOrderByCreatedAtDesc()
                : announcements.findTop50BySenderOrderByCreatedAtDesc(u);
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            throw new BookingException("Pick who should get it.");
        }
    }
}
