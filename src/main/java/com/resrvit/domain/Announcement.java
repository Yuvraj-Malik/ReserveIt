package com.resrvit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "announcements")
@Getter
@Setter
public class Announcement extends BaseEntity {

    @ManyToOne
    private User sender;

    private String audience;

    private String message;

    private int recipientCount;

    private LocalDateTime createdAt;
}
