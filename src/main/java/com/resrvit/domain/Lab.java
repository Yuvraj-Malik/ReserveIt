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
@Table(name = "labs")
@Getter
@Setter
public class Lab extends BaseEntity {

    private String code;

    private String name;

    @ManyToOne
    private Department department;

    @ManyToOne
    private User inCharge;
}
