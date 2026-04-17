package com.ticketbooking.repo;

import com.ticketbooking.domain.Booking;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
  @Query("""
      select coalesce(sum(b.quantity), 0)
      from Booking b
      where b.event.id = :eventId
        and b.status = 'CONFIRMED'
      """)
  int sumConfirmedBookedSeats(@Param("eventId") long eventId);

  @Query("""
      select b
      from Booking b
      where b.event.id = :eventId
        and b.userId = :userId
        and b.status = 'CONFIRMED'
      """)
  Optional<Booking> findConfirmedByUserAndEvent(@Param("userId") String userId, @Param("eventId") long eventId);
}

