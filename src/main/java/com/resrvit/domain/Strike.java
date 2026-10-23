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
@Table(name = "strikes")
@Getter
@Setter
public class Strike extends BaseEntity {

    @ManyToOne
    private User user;

    @ManyToOne
    private Booking booking;

    private String reason;

    private LocalDateTime createdAt;

    private boolean waived;

    @ManyToOne
    private User waivedBy;

    private boolean consumed;
}
