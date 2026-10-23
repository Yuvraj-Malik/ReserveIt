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
@Table(name = "approvals")
@Getter
@Setter
public class Approval extends BaseEntity {

    @ManyToOne
    private Booking booking;

    @ManyToOne
    private User actor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ApprovalAction action;

    private String note;

    private LocalDateTime createdAt;
}
