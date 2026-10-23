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
@Table(name = "timetable_slots")
@Getter
@Setter
public class TimetableSlot extends BaseEntity {

    @ManyToOne
    private Room room;

    private int dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    private String courseCode;

    private String batchLabel;
}
