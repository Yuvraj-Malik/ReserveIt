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

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByRequesterOrderByCreatedAtDesc(User u);

    @Query("select b from Booking b where b.group in :groups order by b.createdAt desc")
    List<Booking> findByGroups(@Param("groups") Collection<StudyGroup> groups);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.EQUIPMENT and b.lab = :lab and b.equipmentType = :type and b.slotDate = :d and b.status in :statuses order by b.queueRank asc")
    List<Booking> equipmentOnDay(@Param("lab") Lab lab, @Param("type") EquipmentType type, @Param("d") LocalDate d, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.EQUIPMENT and b.lab = :lab and b.equipmentType = :type and b.slotDate between :from and :to and b.status in :statuses")
    List<Booking> equipmentBetween(@Param("lab") Lab lab, @Param("type") EquipmentType type, @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.EQUIPMENT and b.equipment = :e and b.slotDate = :d and b.status in :statuses")
    List<Booking> forItemOnDay(@Param("e") Equipment e, @Param("d") LocalDate d, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.EQUIPMENT and b.requester = :u and b.group is null and b.equipmentType = :type and b.slotDate > :from and b.slotDate < :to and b.status in :statuses")
    List<Booking> personalEquipmentWithin(@Param("u") User u, @Param("type") EquipmentType type, @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.EQUIPMENT and b.group in :groups and b.equipmentType = :type and b.slotDate > :from and b.slotDate < :to and b.status in :statuses")
    List<Booking> groupEquipmentWithin(@Param("groups") Collection<StudyGroup> groups, @Param("type") EquipmentType type, @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.room = :room and b.kind <> com.resrvit.domain.BookingKind.SOCIETY_ROOM and b.slotDate = :d and b.status in :statuses order by b.startTime")
    List<Booking> roomOnDay(@Param("room") Room room, @Param("d") LocalDate d, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.room = :room and b.kind = com.resrvit.domain.BookingKind.SOCIETY_ROOM and b.slotDate <= :to and b.endDate >= :from and b.status in :statuses")
    List<Booking> societyOverlapping(@Param("room") Room room, @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.GD_ROOM and b.slotDate = :d and b.status in :statuses and (b.requester = :u or (b.group is not null and b.group.id in :groupIds))")
    List<Booking> gdForUserOnDay(@Param("u") User u, @Param("groupIds") Collection<Long> groupIds, @Param("d") LocalDate d, @Param("statuses") Collection<BookingStatus> statuses);

    List<Booking> findByKindAndStatusOrderByQueueRankAsc(BookingKind kind, BookingStatus status);

    List<Booking> findByStatusOrderByQueueRankAsc(BookingStatus status);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.EQUIPMENT and b.status = com.resrvit.domain.BookingStatus.PENDING and b.lab in :labs order by b.slotDate, b.queueRank")
    List<Booking> pendingEquipmentForLabs(@Param("labs") Collection<Lab> labs);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.LAB_ROOM and b.status = com.resrvit.domain.BookingStatus.PENDING and b.room in :rooms order by b.slotDate, b.startTime, b.queueRank")
    List<Booking> pendingRoomsFor(@Param("rooms") Collection<Room> rooms);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.EQUIPMENT and b.lab = :lab and b.slotDate between :from and :to and b.status in :statuses order by b.slotDate, b.queueRank")
    List<Booking> labEquipmentBetween(@Param("lab") Lab lab, @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.status in :statuses and b.slotDate <= :to and coalesce(b.endDate, b.slotDate) >= :from order by b.slotDate")
    List<Booking> overlappingRange(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.SOCIETY_ROOM and b.society = :s and b.room = :room and b.status in :statuses")
    List<Booking> societyLive(@Param("s") Society s, @Param("room") Room room, @Param("statuses") Collection<BookingStatus> statuses);

    List<Booking> findBySocietyOrderBySlotDateDesc(Society s);

    Optional<Booking> findByQrToken(String token);

    @Query("select b from Booking b where b.critical = true and b.status in :statuses order by b.slotDate")
    List<Booking> criticalBookings(@Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.EQUIPMENT and b.lab = :lab and b.status = com.resrvit.domain.BookingStatus.APPROVED and b.slotDate = :d order by b.queueRank")
    List<Booking> approvedEquipmentForLabOn(@Param("lab") Lab lab, @Param("d") LocalDate d);

    List<Booking> findAllByOrderByCreatedAtDesc();

    boolean existsByGroupAndStatusIn(StudyGroup g, Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.requester = :u and b.status in :statuses")
    List<Booking> liveForRequester(@Param("u") User u, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.status = com.resrvit.domain.BookingStatus.APPROVED and b.slotDate <= :to and coalesce(b.endDate, b.slotDate) >= :from order by b.protectedEval desc, b.queueRank")
    List<Booking> approvedInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select b from Booking b where b.kind = com.resrvit.domain.BookingKind.EQUIPMENT and b.reason in (com.resrvit.domain.BookingReason.EVALUATION_PRACTICE, com.resrvit.domain.BookingReason.COURSEWORK) and b.evaluation is null and b.lab = :lab and b.slotDate <= :d and b.status in :statuses")
    List<Booking> unprotectedEvalPractice(@Param("lab") Lab lab, @Param("d") LocalDate d, @Param("statuses") Collection<BookingStatus> statuses);
}
