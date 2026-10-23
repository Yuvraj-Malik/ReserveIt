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

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByAssetTagIgnoreCase(String tag);
    List<Equipment> findAllByOrderByAssetTagAsc();
    List<Equipment> findByLabOrderByAssetTagAsc(Lab lab);
    List<Equipment> findByLabAndTypeAndStatus(Lab lab, EquipmentType type, EquipmentStatus status);
    List<Equipment> findByLabAndType(Lab lab, EquipmentType type);
    @Query("select distinct e.type from Equipment e where e.lab = :lab order by e.type.name")
    List<EquipmentType> typesInLab(@Param("lab") Lab lab);
}
