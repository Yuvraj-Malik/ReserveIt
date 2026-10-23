package com.resrvit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    private String email;

    private String passwordHash;

    private String fullName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Role role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UserStatus status;

    @ManyToOne
    private Department department;

    @ManyToOne
    private Lab lab;

    private String rollNo;

    private boolean onLeave;

    private LocalDate blockedUntil;

    private String otpCode;

    private LocalDateTime otpExpiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime deactivatedAt;

    @Version
    private Long version;

    /** Small JPEG profile photo as a data URL (resized on upload). */
    private String avatar;

    private String phone;

    private boolean emailNotifications = true;

    /** light, dark or system. */
    private String theme = "light";

    /** Google account id, set on first Google sign-in. */
    private String googleSub;

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isBlockedOn(LocalDate day) {
        return blockedUntil != null && !day.isAfter(blockedUntil);
    }

    public boolean hasRole(Role r) {
        return role == r;
    }

    public String getInitials() {
        String[] parts = fullName == null ? new String[0] : fullName.replace("Dr. ", "").trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        String a = parts[0].substring(0, 1);
        String b = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase();
    }

    public boolean isStaff() {
        return role != Role.STUDENT;
    }

    /** Admins and lab assistants can send announcements. */
    public boolean isAnnouncer() {
        return role == Role.SUPER_ADMIN || role == Role.GD_ADMIN || role == Role.SOCIETY_ADMIN || role == Role.LAB_ASSISTANT;
    }

    public String getDisplayName() {
        return rollNo != null && !rollNo.isBlank() ? fullName + " (" + rollNo + ")" : fullName;
    }
}
