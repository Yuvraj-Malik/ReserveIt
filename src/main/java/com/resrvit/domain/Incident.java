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
@Table(name = "incidents")
@Getter
@Setter
public class Incident extends BaseEntity {

    @ManyToOne
    private Booking booking;

    @ManyToOne
    private Equipment equipment;

    @ManyToOne
    private Lab lab;

    @ManyToOne
    private User reportedBy;

    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private IncidentStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    @ManyToOne
    private User resolvedBy;

    private String resolution;
}
