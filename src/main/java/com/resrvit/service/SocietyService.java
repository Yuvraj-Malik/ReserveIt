package com.resrvit.service;

import com.resrvit.domain.*;
import com.resrvit.repo.SocietyOfficeBearerRepository;
import com.resrvit.repo.SocietyRepository;
import com.resrvit.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Society Room Admins register societies and their office-bearers. */
@Service
@RequiredArgsConstructor
public class SocietyService {

    private final SocietyRepository societies;
    private final SocietyOfficeBearerRepository bearers;
    private final UserRepository users;
    private final NotificationService notifications;
    private final TimeService time;

    @Transactional
    public Society create(String name, String description) {
        if (name == null || name.isBlank()) throw new BookingException("Give the society a name.");
        if (societies.findAll().stream().anyMatch(s -> s.getName().equalsIgnoreCase(name.trim()))) {
            throw new BookingException("A society with that name already exists.");
        }
        Society s = new Society();
        s.setName(name.trim());
        s.setDescription(description == null ? null : description.trim());
        s.setActive(true);
        s.setCreatedAt(time.now());
        return societies.save(s);
    }

    @Transactional
    public void setActive(Society s, boolean active) {
        s.setActive(active);
        societies.save(s);
    }

    @Transactional
    public void addBearer(Society s, String email, String position) {
        User u = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> new BookingException("No account with email " + email + "."));
        if (!u.isActive()) throw new BookingException(u.getFullName() + "'s account isn't active.");
        if (bearers.existsBySocietyAndUser(s, u)) throw new BookingException(u.getFullName() + " is already an office-bearer.");
        if (position == null || position.isBlank()) throw new BookingException("Give the position (for example President).");
        SocietyOfficeBearer b = new SocietyOfficeBearer();
        b.setSociety(s);
        b.setUser(u);
        b.setPosition(position.trim());
        bearers.save(b);
        notifications.notify(u, "You are now " + b.getPosition() + " of " + s.getName() + " and can request society rooms.", "/bookings/new/society");
    }

    @Transactional
    public void removeBearer(SocietyOfficeBearer b) {
        bearers.delete(b);
    }

    public List<Society> forBearer(User u) {
        return bearers.findByUser(u).stream().map(SocietyOfficeBearer::getSociety).filter(Society::isActive).toList();
    }
}
