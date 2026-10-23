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
@Table(name = "evaluation_schedules")
@Getter
@Setter
public class EvaluationSchedule extends BaseEntity {

    @ManyToOne
    private User faculty;

    @ManyToOne
    private Lab lab;

    @ManyToOne
    private EquipmentType equipmentType;

    private String courseCode;

    private String title;

    private LocalDate evalDate;

    private LocalDateTime createdAt;
}
