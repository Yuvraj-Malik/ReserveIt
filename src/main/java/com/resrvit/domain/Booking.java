package com.resrvit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One request or booking of any kind: equipment (one day), a GD slot, a lab/seminar room
 * time range, or a society room date range.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking extends BaseEntity {

    /** Statuses that hold (or are waiting for) a resource. */
    public static final Set<BookingStatus> LIVE =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.APPROVED, BookingStatus.CHECKED_OUT);
    /** Statuses that count against fair-use limits. */
    public static final Set<BookingStatus> COUNTS_FOR_LIMIT =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.APPROVED, BookingStatus.CHECKED_OUT, BookingStatus.RETURNED);
    /** Statuses that actually occupy the resource. */
    public static final Set<BookingStatus> OCCUPYING =
            EnumSet.of(BookingStatus.APPROVED, BookingStatus.CHECKED_OUT);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private BookingKind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private BookingStatus status;

    @ManyToOne
    private User requester;

    @ManyToOne
    private StudyGroup group;

    @ManyToOne
    private Society society;

    @ManyToOne
    private Room room;

    @ManyToOne
    private Lab lab;

    @ManyToOne
    private EquipmentType equipmentType;

    /** The specific item, assigned on approval (items of a type are interchangeable). */
    @ManyToOne
    private Equipment equipment;

    private LocalDate slotDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private BookingReason reason;

    private String reasonText;

    @ManyToOne
    private EvaluationSchedule evaluation;

    private boolean protectedEval;
    private boolean urgent;
    private boolean critical;
    private String criticalNote;
    private boolean directBooking;
    private boolean emergency;

    /** Position in its queue; lower goes first. Starts as creation time in millis. */
    private long queueRank;

    /** Bumped with nothing free within 7 days: stays first in line for the next item. */
    private boolean waitlistPriority;

    @ManyToOne
    private User claimedBy;
    private LocalDateTime claimedAt;

    @ManyToOne
    private User decidedBy;
    private LocalDateTime decidedAt;
    private String decisionNote;

    private String qrToken;

    @ManyToOne
    private Booking previousBooking;

    private boolean renewalReminderSent;

    /** Society bookings: comma-separated ISO dates excluded because the timetable blocks them. */
    private String excludedDates;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isLive() {
        return LIVE.contains(status);
    }

    public boolean isPending() {
        return status == BookingStatus.PENDING;
    }

    public boolean isForEquipment() {
        return kind == BookingKind.EQUIPMENT;
    }

    /** When the booked slot begins; used for expiry. */
    public LocalDateTime getStartsAt() {
        return switch (kind) {
            case EQUIPMENT -> slotDate.atStartOfDay();
            case SOCIETY_ROOM -> slotDate.atStartOfDay();
            default -> slotDate.atTime(startTime != null ? startTime : LocalTime.MIDNIGHT);
        };
    }

    /** Queue key: requests for the same resource and slot compete with each other. */
    public String getQueueKey() {
        return switch (kind) {
            case EQUIPMENT -> "EQ:" + lab.getId() + ":" + equipmentType.getId() + ":" + slotDate;
            case GD_ROOM, LAB_ROOM -> "RM:" + room.getId() + ":" + slotDate + ":" + startTime;
            case SOCIETY_ROOM -> "SO:" + room.getId() + ":" + slotDate;
        };
    }

    public String getResourceLabel() {
        return switch (kind) {
            case EQUIPMENT -> equipmentType.getName() + (equipment != null ? " · " + equipment.getAssetTag() : "")
                    + " — " + lab.getName();
            default -> room.getName();
        };
    }

    public String getWhenLabel() {
        return switch (kind) {
            case EQUIPMENT -> DATE.format(slotDate);
            case SOCIETY_ROOM -> DATE.format(slotDate) + " – " + DATE.format(endDate);
            default -> DATE.format(slotDate) + ", " + TIME.format(startTime) + " – " + TIME.format(endTime);
        };
    }

    public String getForLabel() {
        if (society != null) return society.getName();
        if (group != null) return "Group: " + group.getName();
        return requester.getDisplayName();
    }

    public List<LocalDate> getExcludedDateList() {
        if (excludedDates == null || excludedDates.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(excludedDates.split(",")).map(String::trim).map(LocalDate::parse).toList());
    }

    public String getStatusBadge() {
        return switch (status) {
            case PENDING -> "warning";
            case APPROVED -> "success";
            case CHECKED_OUT -> "primary";
            case RETURNED -> "secondary";
            case REJECTED, EXPIRED -> "danger";
            case CANCELLED -> "dark";
        };
    }
}
