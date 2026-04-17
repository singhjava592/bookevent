package com.ticketbooking.service;

import com.ticketbooking.config.Config;
import com.ticketbooking.domain.EventStatus;
import com.ticketbooking.repo.BookingRepository;
import com.ticketbooking.repo.EventRepository;
import com.ticketbooking.repo.SeatHoldRepository;
import java.time.LocalDateTime;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class AvailabilityService {
  public record Availability(long eventId, int totalSeats, int heldSeats, int bookedSeats, int availableSeats) {}

  private final EventRepository eventRepo;
  private final SeatHoldRepository holdRepo;
  private final BookingRepository bookingRepo;

  public AvailabilityService(EventRepository eventRepo, SeatHoldRepository holdRepo, BookingRepository bookingRepo) {
    this.eventRepo = eventRepo;
    this.holdRepo = holdRepo;
    this.bookingRepo = bookingRepo;
  }

  @Cacheable(cacheNames = Config.AVAILABILITY_CACHE, key = "#eventId")
  public Availability getAvailability(long eventId) {
    var event =
        eventRepo
            .findByIdAndStatus(eventId, EventStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("Event not found"));
    var now = LocalDateTime.now();
    var held = holdRepo.sumActiveHeldSeats(eventId, now);
    var booked = bookingRepo.sumConfirmedBookedSeats(eventId);
    var available = Math.max(0, event.getTotalSeats() - held - booked);
    return new Availability(event.getId(), event.getTotalSeats(), held, booked, available);
  }
}

