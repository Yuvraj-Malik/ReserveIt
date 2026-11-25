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
@Table(name = "society_office_bearers")
@Getter
@Setter
public class SocietyOfficeBearer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Society society;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    private String position;
}
