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
@Table(name = "messages")
@Getter
@Setter
public class Message extends BaseEntity {

    @ManyToOne
    private Conversation conversation;

    @ManyToOne
    private User sender;

    private String ciphertext;

    private String iv;

    private LocalDateTime createdAt;

    /** Decrypted text, filled in by ChatService; never stored. */
    @Transient
    private String plaintext;
}
