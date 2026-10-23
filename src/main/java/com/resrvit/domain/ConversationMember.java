package com.resrvit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Membership of a direct chat, and read position for any chat. */
@Entity
@Table(name = "conversation_members")
@Getter
@Setter
public class ConversationMember extends BaseEntity {

    @ManyToOne
    private Conversation conversation;

    @ManyToOne
    private User user;

    private LocalDateTime lastReadAt;
}
