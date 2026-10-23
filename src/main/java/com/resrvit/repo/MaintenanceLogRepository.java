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

public interface MaintenanceLogRepository extends JpaRepository<MaintenanceLog, Long> {

    List<MaintenanceLog> findByActiveTrueOrderByStartDateAsc();
    List<MaintenanceLog> findAllByOrderByCreatedAtDesc();
    @Query("select m from MaintenanceLog m where m.active = true and m.equipment = :e and m.startDate <= :d and m.endDate >= :d")
    List<MaintenanceLog> activeForEquipment(@Param("e") Equipment e, @Param("d") LocalDate d);
    @Query("select m from MaintenanceLog m where m.active = true and m.room = :r and m.startDate <= :to and m.endDate >= :from")
    List<MaintenanceLog> activeForRoom(@Param("r") Room r, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
