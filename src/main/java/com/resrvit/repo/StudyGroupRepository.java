package com.resrvit.repo;

import com.resrvit.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {

    @Query("select distinct g from StudyGroup g join g.members m where m.user = :u and g.status <> com.resrvit.domain.GroupStatus.DISSOLVED order by g.createdAt desc")
    List<StudyGroup> findForMember(@Param("u") User u);
    List<StudyGroup> findByCourseFacultyAndStatus(User f, GroupStatus s);
    List<StudyGroup> findByCourseFacultyOrCreatedByOrderByCreatedAtDesc(User f, User c);
    List<StudyGroup> findAllByOrderByCreatedAtDesc();
    @Query("select distinct g from StudyGroup g join g.members m where m.user = :u and g.courseCode = :course and g.status <> com.resrvit.domain.GroupStatus.DISSOLVED")
    List<StudyGroup> findActiveForMemberAndCourse(@Param("u") User u, @Param("course") String course);
}
