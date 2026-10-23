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

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop30ByUserOrderByCreatedAtDesc(User u);
    List<Notification> findTop100ByUserOrderByCreatedAtDesc(User u);
    @Modifying
    @Query("delete from Notification n where n.user = :u")
    int deleteAllForUser(@Param("u") User u);
    long countByUserAndReadFalse(User u);
    @Modifying
    @Query("update Notification n set n.read = true where n.user = :u and n.read = false")
    int markAllRead(@Param("u") User u);
}
