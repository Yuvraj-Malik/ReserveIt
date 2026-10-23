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
@Table(name = "slot_offers")
@Getter
@Setter
public class SlotOffer extends BaseEntity {

    @ManyToOne
    private Booking booking;

    private LocalDate offeredDate;

    private int optionRank;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private OfferStatus status;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
}
