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
@Table(name = "queue_audit")
@Getter
@Setter
public class QueueAudit extends BaseEntity {

    @ManyToOne
    private Booking booking;

    @ManyToOne
    private User actor;

    private long oldRank;

    private long newRank;

    private String reason;

    private LocalDateTime createdAt;
}
