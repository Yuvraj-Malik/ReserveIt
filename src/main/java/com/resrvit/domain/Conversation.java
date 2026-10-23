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
@Table(name = "conversations")
@Getter
@Setter
public class Conversation extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ConversationKind kind = ConversationKind.BOOKING;

    public boolean isDirect() {
        return kind == ConversationKind.DIRECT;
    }

    @ManyToOne
    private User student;

    @ManyToOne
    private Lab lab;

    @ManyToOne
    private Booking booking;

    private String subject;

    private boolean urgent;

    private String urgentReason;

    private LocalDateTime urgentAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ConversationStatus status;

    private Integer rating;

    private LocalDateTime createdAt;

    private LocalDateTime lastMessageAt;

    private LocalDateTime firstResponseAt;
}
