package com.resrvit.service;

import com.resrvit.domain.User;
import com.resrvit.repo.DepartmentRepository;
import com.resrvit.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;

/** The parts of Settings every user controls: profile, photo and preferences. */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final int AVATAR_PX = 160;
    private static final Set<String> THEMES = Set.of("light", "dark", "system");

    private final UserRepository users;
    private final DepartmentRepository departments;

    @Transactional
    public void updateProfile(User u, String fullName, String phone, Long departmentId) {
        if (fullName == null || fullName.isBlank()) throw new BookingException("Enter your name.");
        if (fullName.length() > 120) throw new BookingException("That name is too long.");
        if (phone != null && !phone.isBlank() && !phone.trim().matches("[+0-9 ()-]{7,20}")) {
            throw new BookingException("Enter a valid phone number.");
        }
        u.setFullName(fullName.trim());
        u.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        u.setDepartment(departmentId == null ? null : departments.findById(departmentId).orElse(null));
        users.save(u);
    }

    @Transactional
    public void updatePreferences(User u, boolean emailNotifications, String theme) {
        u.setEmailNotifications(emailNotifications);
        u.setTheme(THEMES.contains(theme) ? theme : "light");
        users.save(u);
    }

    /** Crops to a square, scales to 160 px and stores as a small JPEG data URL. */
    @Transactional
    public void setAvatar(User u, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new BookingException("Choose an image.");
        if (bytes.length > 5 * 1024 * 1024) throw new BookingException("Use an image under 5 MB.");
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            src = null;
        }
        if (src == null) throw new BookingException("That file isn't a JPEG or PNG image.");
        int side = Math.min(src.getWidth(), src.getHeight());
        BufferedImage square = src.getSubimage((src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
        BufferedImage out = new BufferedImage(AVATAR_PX, AVATAR_PX, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, AVATAR_PX, AVATAR_PX);
        g.drawImage(square, 0, 0, AVATAR_PX, AVATAR_PX, null);
        g.dispose();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            ImageIO.write(out, "jpg", buf);
        } catch (IOException e) {
            throw new BookingException("Couldn't process that image.");
        }
        u.setAvatar("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(buf.toByteArray()));
        users.save(u);
    }

    @Transactional
    public void removeAvatar(User u) {
        u.setAvatar(null);
        users.save(u);
    }
}
