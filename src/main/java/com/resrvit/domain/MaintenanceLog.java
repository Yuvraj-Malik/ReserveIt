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
@Table(name = "maintenance_logs")
@Getter
@Setter
public class MaintenanceLog extends BaseEntity {

    @ManyToOne
    private Equipment equipment;

    @ManyToOne
    private Room room;

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    @ManyToOne
    private User createdBy;

    private boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime closedAt;

    public boolean covers(LocalDate d) {
        return active && !d.isBefore(startDate) && !d.isAfter(endDate);
    }

    public String getTargetLabel() {
        if (equipment != null) return equipment.getAssetTag() + " (" + equipment.getType().getName() + ")";
        return room != null ? room.getName() : "-";
    }
}
