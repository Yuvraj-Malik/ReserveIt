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
@Table(name = "rooms")
@Getter
@Setter
public class Room extends BaseEntity {

    private String code;

    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private RoomType type;

    @ManyToOne
    private Department department;

    @ManyToOne
    private Lab lab;

    @ManyToOne
    private User inCharge;

    private int capacity;

    private boolean bookable;

    /** Faculty member who approves this room: its own in-charge, else its lab's in-charge. */
    public User getApprover() {
        if (inCharge != null) return inCharge;
        return lab != null ? lab.getInCharge() : null;
    }
}
