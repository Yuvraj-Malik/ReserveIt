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
@Table(name = "group_members")
@Getter
@Setter
public class GroupMember extends BaseEntity {

    @ManyToOne
    private StudyGroup group;

    @ManyToOne
    private User user;

    private boolean confirmed;
}
