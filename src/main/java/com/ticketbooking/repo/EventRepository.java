package com.ticketbooking.repo;

import com.ticketbooking.domain.Event;
import com.ticketbooking.domain.EventStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface EventRepository extends JpaRepository<Event, Long> {
  Optional<Event> findByIdAndStatus(Long id, EventStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Event e where e.id = :id and e.status = 'ACTIVE'")
  Optional<Event> findActiveByIdForUpdate(@Param("id") Long id);
}

