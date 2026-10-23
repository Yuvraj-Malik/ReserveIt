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

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByStudentOrderByLastMessageAtDesc(User s);
    @Query("select c from Conversation c where c.lab = :lab order by case when c.status = com.resrvit.domain.ConversationStatus.OPEN then 0 else 1 end, case when c.urgent = true then 0 else 1 end, c.lastMessageAt desc")
    List<Conversation> inbox(@Param("lab") Lab lab);
    @Query("select c from Conversation c order by case when c.status = com.resrvit.domain.ConversationStatus.OPEN then 0 else 1 end, case when c.urgent = true then 0 else 1 end, c.lastMessageAt desc")
    List<Conversation> allInbox();
    long countByStudentAndUrgentAtAfter(User s, LocalDateTime t);
    Optional<Conversation> findFirstByBooking(Booking b);
    List<Conversation> findByKindOrderByLastMessageAtDesc(ConversationKind kind);
}
