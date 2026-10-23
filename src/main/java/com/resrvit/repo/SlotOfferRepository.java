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

public interface SlotOfferRepository extends JpaRepository<SlotOffer, Long> {

    List<SlotOffer> findByBookingAndStatusOrderByOptionRankAsc(Booking b, OfferStatus s);
    List<SlotOffer> findByStatusAndExpiresAtBefore(OfferStatus s, LocalDateTime t);
    @Query("select o from SlotOffer o where o.status = com.resrvit.domain.OfferStatus.OPEN and o.booking.requester = :u order by o.booking.id, o.optionRank")
    List<SlotOffer> findOpenForUser(@Param("u") User u);
}
