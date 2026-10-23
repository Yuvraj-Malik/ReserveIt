package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Chat rules:
 * <ul>
 *   <li>Staff (anyone who isn't a student) can chat one-to-one with any other staff member at any time.</li>
 *   <li>A student can't start a chat. When raising a request they may add one note; it opens the
 *       booking's thread with the staff who handle that booking.</li>
 *   <li>The booking thread unlocks for both sides once the request is approved (the equipment or room
 *       is assigned) and stays open while the item is out. Before and after that it is read-only.</li>
 * </ul>
 * Messages are encrypted at rest (AES-256-GCM, see {@link CryptoService}).
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("h:mm a");
    public static final int MAX_MESSAGE = 2000;

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final UserRepository users;
    private final CryptoService crypto;
    private final NotificationService notifications;
    private final ApprovalService approvals;
    private final QueueEvents events;
    private final TimeService time;

    /** One row in the chat list. */
    public record ThreadView(Conversation conversation, String title, String subtitle, String avatar, String initials,
                             String preview, LocalDateTime lastAt, long unread, boolean locked) {}

    // ------------------------------------------------------------------ booking threads

    /** The student's one note at request time. Opens the booking thread (read-only until approval). */
    @Transactional
    public void postRequestNote(Booking b, User student, String text) {
        if (text == null || text.isBlank()) return;
        Conversation c = bookingThread(b);
        if (!messages.findByConversationOrderByCreatedAtAsc(c).isEmpty()) {
            throw new BookingException("You've already added a note to this request.");
        }
        store(c, student, text);
        notifyOthers(c, student, "Note on request #" + b.getId() + " from " + student.getFullName());
    }

    @Transactional
    public Conversation bookingThread(Booking b) {
        return conversations.findFirstByBooking(b).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setKind(ConversationKind.BOOKING);
            c.setBooking(b);
            c.setStudent(b.getRequester());
            c.setLab(b.getLab());
            c.setSubject("#" + b.getId() + " · " + b.getResourceLabel());
            c.setStatus(ConversationStatus.OPEN);
            c.setCreatedAt(time.now());
            c.setLastMessageAt(time.now());
            return conversations.save(c);
        });
    }

    /** Booking threads are writable while the booking is approved or the item is out. */
    public boolean isUnlocked(Conversation c) {
        if (c.isDirect()) return true;
        Booking b = c.getBooking();
        return b != null && (b.getStatus() == BookingStatus.APPROVED || b.getStatus() == BookingStatus.CHECKED_OUT);
    }

    public String lockReason(Conversation c) {
        if (isUnlocked(c)) return null;
        Booking b = c.getBooking();
        if (b == null) return "This chat is closed.";
        return switch (b.getStatus()) {
            case PENDING -> "Chat opens once the request is approved.";
            case RETURNED -> "The item was returned, so this chat is closed.";
            default -> "This request is " + b.getStatus().getLabel().toLowerCase() + ", so the chat is closed.";
        };
    }

    // ------------------------------------------------------------------ direct staff chats

    @Transactional
    public Conversation direct(User me, Long otherId) {
        if (!me.isStaff()) throw new BookingException("Students can only chat inside their approved bookings.");
        User other = users.findById(otherId == null ? -1 : otherId).orElseThrow(() -> new BookingException("Pick someone to chat with."));
        if (!other.isStaff() || !other.isActive()) throw new BookingException("You can only start chats with active staff.");
        if (other.sameAs(me)) throw new BookingException("Pick someone else.");
        for (ConversationMember m : members.findByUser(me)) {
            Conversation c = m.getConversation();
            if (c.isDirect() && members.findByConversationAndUser(c, other).isPresent()) return c;
        }
        Conversation c = new Conversation();
        c.setKind(ConversationKind.DIRECT);
        c.setSubject("Direct chat");
        c.setStatus(ConversationStatus.OPEN);
        c.setCreatedAt(time.now());
        c.setLastMessageAt(time.now());
        conversations.save(c);
        for (User u : List.of(me, other)) {
            ConversationMember m = new ConversationMember();
            m.setConversation(c);
            m.setUser(u);
            members.save(m);
        }
        return c;
    }

    // ------------------------------------------------------------------ messages

    @Transactional
    public Message send(Conversation c, User sender, String text) {
        if (!canView(c, sender)) throw new BookingException("You can't post in this chat.");
        if (!isUnlocked(c)) throw new BookingException(lockReason(c));
        Message m = store(c, sender, text);
        notifyOthers(c, sender, sender.getFullName() + ": " + preview(text));
        return m;
    }

    private Message store(Conversation c, User sender, String text) {
        if (text == null || text.isBlank()) throw new BookingException("Write a message.");
        text = text.trim();
        if (text.length() > MAX_MESSAGE) throw new BookingException("Messages are limited to " + MAX_MESSAGE + " characters.");
        CryptoService.Sealed sealed = crypto.encrypt(text, c.getId());
        Message m = new Message();
        m.setConversation(c);
        m.setSender(sender);
        m.setCiphertext(sealed.ciphertext());
        m.setIv(sealed.iv());
        m.setCreatedAt(time.now());
        messages.save(m);
        m.setPlaintext(text);
        c.setLastMessageAt(m.getCreatedAt());
        if (!c.isDirect() && sender.isStaff() && c.getFirstResponseAt() == null) c.setFirstResponseAt(m.getCreatedAt());
        conversations.save(c);
        markRead(c, sender);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", m.getId());
        payload.put("senderId", sender.getId());
        payload.put("sender", sender.getFullName());
        payload.put("initials", sender.getInitials());
        payload.put("role", sender.getRole().getLabel());
        payload.put("text", text);
        payload.put("at", TS.format(m.getCreatedAt()));
        events.conversationMessage(c.getId(), payload);
        for (User u : participants(c)) events.userChanged(u.getId(), "chat");
        return m;
    }

    private void notifyOthers(Conversation c, User sender, String text) {
        List<User> others = participants(c).stream().filter(u -> !u.sameAs(sender)).toList();
        notifications.notifyAllQuietly(others, text, "/chat/" + c.getId());
    }

    public List<Message> messages(Conversation c) {
        List<Message> list = messages.findByConversationOrderByCreatedAtAsc(c);
        list.forEach(m -> m.setPlaintext(crypto.decrypt(m.getCiphertext(), m.getIv(), c.getId())));
        return list;
    }

    @Transactional
    public void markRead(Conversation c, User u) {
        ConversationMember m = members.findByConversationAndUser(c, u).orElseGet(() -> {
            ConversationMember n = new ConversationMember();
            n.setConversation(c);
            n.setUser(u);
            return n;
        });
        m.setLastReadAt(time.now());
        members.save(m);
    }

    public long unread(Conversation c, User u) {
        LocalDateTime since = members.findByConversationAndUser(c, u).map(ConversationMember::getLastReadAt).orElse(null);
        return since == null ? messages.countByConversationAndSenderNot(c, u)
                : messages.countByConversationAndSenderNotAndCreatedAtAfter(c, u, since);
    }

    // ------------------------------------------------------------------ who sees what

    /** Everyone in a chat: the two staff in a direct chat; for a booking, the student side plus the staff who handle it. */
    public List<User> participants(Conversation c) {
        if (c.isDirect()) return members.findByConversation(c).stream().map(ConversationMember::getUser).toList();
        Booking b = c.getBooking();
        Set<User> out = new LinkedHashSet<>();
        out.add(b.getRequester());
        if (b.getGroup() != null) b.getGroup().getMembers().forEach(m -> out.add(m.getUser()));
        out.addAll(handlers(b));
        return new ArrayList<>(out);
    }

    /** Staff who handle a booking (not the super admin, who can read every booking chat). */
    public List<User> handlers(Booking b) {
        List<User> out = new ArrayList<>();
        switch (b.getKind()) {
            case EQUIPMENT -> {
                out.addAll(approvals.labAssistants(b.getLab()));
                if (b.getLab().getInCharge() != null) out.add(b.getLab().getInCharge());
            }
            case LAB_ROOM -> { if (b.getRoom().getApprover() != null) out.add(b.getRoom().getApprover()); }
            case GD_ROOM -> out.addAll(users.findByRoleAndStatus(Role.GD_ADMIN, UserStatus.ACTIVE));
            case SOCIETY_ROOM -> out.addAll(users.findByRoleAndStatus(Role.SOCIETY_ADMIN, UserStatus.ACTIVE));
        }
        return out;
    }

    public boolean canView(Conversation c, User u) {
        if (c.isDirect()) return members.findByConversationAndUser(c, u).isPresent();
        Booking b = c.getBooking();
        if (b.getRequester().sameAs(u)) return true;
        if (b.getGroup() != null && b.getGroup().hasMember(u)) return true;
        if (u.getRole() == Role.SUPER_ADMIN) return true;
        return handlers(b).stream().anyMatch(h -> h.sameAs(u));
    }

    private boolean isParticipantOrHandler(Conversation c, User u) {
        return participants(c).stream().anyMatch(p -> p.sameAs(u));
    }

    /** The chat list for a user, newest first, with unread counts. */
    public List<ThreadView> inbox(User u) {
        List<Conversation> all = new ArrayList<>();
        for (ConversationMember m : members.findByUser(u)) {
            if (m.getConversation().isDirect()) all.add(m.getConversation());
        }
        for (Conversation c : conversations.findByKindOrderByLastMessageAtDesc(ConversationKind.BOOKING)) {
            if (c.getBooking() == null || !canView(c, u)) continue;
            // The super admin can read every booking chat, but only lists the ones they've opened.
            if (u.getRole() == Role.SUPER_ADMIN && !isParticipantOrHandler(c, u) && members.findByConversationAndUser(c, u).isEmpty()) continue;
            all.add(c);
        }
        List<ThreadView> out = new ArrayList<>();
        for (Conversation c : all) out.add(view(c, u));
        out.sort(Comparator.comparing(ThreadView::lastAt).reversed());
        return out;
    }

    public ThreadView view(Conversation c, User u) {
        String title, subtitle, avatar = null, initials;
        if (c.isDirect()) {
            User other = participants(c).stream().filter(x -> !x.sameAs(u)).findFirst().orElse(u);
            title = other.getFullName();
            subtitle = other.getRole().getLabel() + (other.getLab() != null ? " · " + other.getLab().getName() : "");
            avatar = other.getAvatar();
            initials = other.getInitials();
        } else {
            Booking b = c.getBooking();
            boolean studentSide = b.getRequester().sameAs(u) || (b.getGroup() != null && b.getGroup().hasMember(u));
            title = studentSide ? b.getResourceLabel() : b.getForLabel();
            subtitle = (studentSide ? "" : b.getResourceLabel() + " · ") + b.getWhenLabel();
            if (!studentSide) avatar = b.getRequester().getAvatar();
            initials = studentSide ? "#" + b.getId() : b.getRequester().getInitials();
        }
        Message last = messages.findFirstByConversationOrderByCreatedAtDesc(c);
        String preview = last == null ? "No messages yet"
                : (last.getSender().sameAs(u) ? "You: " : "") + preview(crypto.decrypt(last.getCiphertext(), last.getIv(), c.getId()));
        return new ThreadView(c, title, subtitle, avatar, initials, preview, c.getLastMessageAt(), unread(c, u), !isUnlocked(c));
    }

    public long totalUnread(User u) {
        return inbox(u).stream().mapToLong(ThreadView::unread).sum();
    }

    /** Staff you can start a direct chat with. */
    public List<User> staffDirectory(User me) {
        return users.findAllByOrderByRoleAscFullNameAsc().stream()
                .filter(x -> x.isActive() && x.isStaff() && !x.sameAs(me)).toList();
    }

    private static String preview(String text) {
        String t = text.replace('\n', ' ');
        return t.length() > 70 ? t.substring(0, 70) + "…" : t;
    }
}
