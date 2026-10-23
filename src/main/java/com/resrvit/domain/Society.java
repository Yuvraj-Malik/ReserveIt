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
@Table(name = "societies")
@Getter
@Setter
public class Society extends BaseEntity {

    private String name;

    private String description;

    private boolean active;

    private LocalDateTime createdAt;
}
