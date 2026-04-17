package com.ticketbooking.repo;

import com.ticketbooking.domain.HoldStatus;
import com.ticketbooking.domain.SeatHold;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatHoldRepository extends JpaRepository<SeatHold, String> {
  @Query("""
      select coalesce(sum(h.quantity), 0)
      from SeatHold h
      where h.event.id = :eventId
        and h.status = 'ACTIVE'
        and h.expiresAt > :now
      """)
  int sumActiveHeldSeats(@Param("eventId") long eventId, @Param("now") LocalDateTime now);

  Optional<SeatHold> findByIdAndStatus(String id, HoldStatus status);

  @Modifying
  @Query("""
      update SeatHold h
         set h.status = 'EXPIRED'
       where h.status = 'ACTIVE'
         and h.expiresAt < :now
      """)
  int expireHolds(@Param("now") LocalDateTime now);
}

