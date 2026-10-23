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
@Table(name = "equipment")
@Getter
@Setter
public class Equipment extends BaseEntity {

    private String assetTag;

    @ManyToOne
    private EquipmentType type;

    @ManyToOne
    private Lab lab;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private EquipmentStatus status;

    private String conditionNote;
}
