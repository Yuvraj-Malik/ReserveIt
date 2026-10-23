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
@Table(name = "study_groups")
@Getter
@Setter
public class StudyGroup extends BaseEntity {

    private String name;

    private String courseCode;

    private String projectTitle;

    @ManyToOne
    private User leader;

    @ManyToOne
    private User createdBy;

    @ManyToOne
    private User courseFaculty;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private GroupStatus status;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private java.util.List<GroupMember> members = new java.util.ArrayList<>();

    public boolean hasMember(User u) {
        return members.stream().anyMatch(m -> m.getUser().getId().equals(u.getId()));
    }

    public boolean isAllConfirmed() {
        return members.stream().allMatch(GroupMember::isConfirmed);
    }
}
