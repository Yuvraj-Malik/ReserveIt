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
@Table(name = "checkouts")
@Getter
@Setter
public class Checkout extends BaseEntity {

    @ManyToOne
    private Booking booking;

    @ManyToOne
    private Equipment equipment;

    @ManyToOne
    private User issuedBy;

    private LocalDateTime issuedAt;

    private String conditionOut;

    private LocalDateTime dueAt;

    private LocalDateTime returnedAt;

    @ManyToOne
    private User receivedBy;

    private String conditionIn;

    private boolean late;

    private boolean damaged;
}
