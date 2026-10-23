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

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findAllByOrderByRoleAscFullNameAsc();
    List<User> findByRoleAndStatus(Role role, UserStatus status);
    List<User> findByStatusOrderByCreatedAtAsc(UserStatus status);
    List<User> findByLabAndRoleAndStatus(Lab lab, Role role, UserStatus status);
    @Query("select u from User u where u.status = com.resrvit.domain.UserStatus.ACTIVE and u.role = com.resrvit.domain.Role.STUDENT order by u.fullName")
    List<User> activeStudents();
    @Query("select u from User u where u.status = com.resrvit.domain.UserStatus.ACTIVE and u.role = com.resrvit.domain.Role.FACULTY order by u.fullName")
    List<User> activeFaculty();
}
