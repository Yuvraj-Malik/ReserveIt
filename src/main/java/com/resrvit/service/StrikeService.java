package com.resrvit.service;

import com.resrvit.domain.Booking;
import com.resrvit.domain.Role;
import com.resrvit.domain.Strike;
import com.resrvit.domain.User;
import com.resrvit.repo.StrikeRepository;
import com.resrvit.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Late returns earn strikes. Three strikes block booking for 14 days; admins can waive a strike. */
@Service
@RequiredArgsConstructor
public class StrikeService {

    private final StrikeRepository strikes;
    private final UserRepository users;
    private final NotificationService notifications;
    private final SettingsService settings;
    private final TimeService time;

    @Transactional
    public Strike add(User user, Booking booking, String reason) {
        Strike s = new Strike();
        s.setUser(user);
        s.setBooking(booking);
        s.setReason(reason);
        s.setCreatedAt(time.now());
        strikes.save(s);
        List<Strike> active = strikes.findByUserAndWaivedFalseAndConsumedFalse(user);
        int limit = settings.getInt(SettingsService.STRIKE_LIMIT, 3);
        if (active.size() >= limit) {
            int days = settings.getInt(SettingsService.STRIKE_BLOCK_DAYS, 14);
            user.setBlockedUntil(time.today().plusDays(days - 1));
            users.save(user);
            active.forEach(a -> a.setConsumed(true));
            strikes.saveAll(active);
            notifications.notify(user, "You have " + limit + " strikes, so booking is blocked until "
                    + DateTimeFormatter.ofPattern("EEE d MMM").format(user.getBlockedUntil()) + ".", "/strikes");
        } else {
            notifications.notify(user, "Strike " + active.size() + " of " + limit + ": " + reason, "/strikes");
        }
        return s;
    }

    @Transactional
    public void waive(Strike s, User admin) {
        if (admin.getRole() != Role.SUPER_ADMIN && admin.getRole() != Role.LAB_ASSISTANT) {
            throw new BookingException("Only admins can waive strikes.");
        }
        if (s.isWaived()) throw new BookingException("Already waived.");
        s.setWaived(true);
        s.setWaivedBy(admin);
        strikes.save(s);
        User u = s.getUser();
        if (s.isConsumed() && u.getBlockedUntil() != null) {
            // The block came from this strike's batch: lift it and count the others again.
            u.setBlockedUntil(null);
            users.save(u);
            for (Strike other : strikes.findByUserOrderByCreatedAtDesc(u)) {
                if (other.isConsumed() && !other.isWaived()) {
                    other.setConsumed(false);
                    strikes.save(other);
                }
            }
        }
        notifications.notify(u, "A strike was waived by " + admin.getFullName() + ".", "/strikes");
    }

    public long activeTotal() {
        return strikes.findAllByOrderByCreatedAtDesc().stream().filter(s -> !s.isWaived() && !s.isConsumed()).count();
    }

    public long activeCount(User u) {
        return strikes.findByUserAndWaivedFalseAndConsumedFalse(u).size();
    }
}
