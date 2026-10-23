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

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByCodeIgnoreCase(String code);
    List<Room> findAllByOrderByNameAsc();
    List<Room> findByTypeAndBookableTrueOrderByNameAsc(RoomType type);
    List<Room> findByTypeInAndBookableTrueOrderByNameAsc(Collection<RoomType> types);
    @Query("select r from Room r where r.bookable = true and r.type in :types and (r.inCharge = :u or (r.inCharge is null and r.lab.inCharge = :u))")
    List<Room> findApprovableBy(@Param("u") User u, @Param("types") Collection<RoomType> types);
}
