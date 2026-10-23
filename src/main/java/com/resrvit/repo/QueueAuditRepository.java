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

public interface QueueAuditRepository extends JpaRepository<QueueAudit, Long> {

    List<QueueAudit> findTop100ByOrderByCreatedAtDesc();
    List<QueueAudit> findByBookingOrderByCreatedAtAsc(Booking b);
}
