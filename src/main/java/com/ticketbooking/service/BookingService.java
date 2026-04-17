package com.ticketbooking.service;

import com.ticketbooking.config.AppProperties;
import com.ticketbooking.config.Config;
import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.EventStatus;
import com.ticketbooking.domain.HoldStatus;
import com.ticketbooking.domain.SeatHold;
import com.ticketbooking.repo.BookingRepository;
import com.ticketbooking.repo.EventRepository;
import com.ticketbooking.repo.SeatHoldRepository;
import java.time.LocalDateTime;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
  public record HoldResult(String holdId, LocalDateTime expiresAt) {}

  private final AppProperties props;
  private final EventRepository eventRepo;
  private final SeatHoldRepository holdRepo;
  private final BookingRepository bookingRepo;
  private final CacheManager cacheManager;

  public BookingService(
      AppProperties props,
      EventRepository eventRepo,
      SeatHoldRepository holdRepo,
      BookingRepository bookingRepo,
      CacheManager cacheManager) {
    this.props = props;
    this.eventRepo = eventRepo;
    this.holdRepo = holdRepo;
    this.bookingRepo = bookingRepo;
    this.cacheManager = cacheManager;
  }

  @Transactional
  public HoldResult holdSeats(long eventId, String userId, int quantity) {
    if (quantity <= 0) throw new ConflictException("Quantity must be > 0");
    var max = props.holds().maxQuantityPerHold();
    if (max > 0 && quantity > max) {
      throw new ConflictException("Quantity exceeds per-hold limit of " + max);
    }

    var event =
        eventRepo
            .findActiveByIdForUpdate(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));

    if (event.getStatus() != EventStatus.ACTIVE) throw new NotFoundException("Event not found");

    // Enforce "prevent double booking for same user & event" (active confirmed only).
    if (bookingRepo.findConfirmedByUserAndEvent(userId, eventId).isPresent()) {
      throw new ConflictException("User already has a confirmed booking for this event");
    }

    var now = LocalDateTime.now();
    var held = holdRepo.sumActiveHeldSeats(eventId, now);
    var booked = bookingRepo.sumConfirmedBookedSeats(eventId);
    var available = event.getTotalSeats() - held - booked;
    if (quantity > available) {
      throw new ConflictException("Not enough seats available");
    }

    var hold = new SeatHold();
    hold.setEvent(event);
    hold.setUserId(userId);
    hold.setQuantity(quantity);
    hold.setStatus(HoldStatus.ACTIVE);
    hold.setExpiresAt(now.plusMinutes(props.holds().ttlMinutes()));
    var saved = holdRepo.save(hold);

    evictAvailability(eventId);
    return new HoldResult(saved.getId(), saved.getExpiresAt());
  }

  @Transactional
  public long confirmHold(String holdId, String userId) {
    var hold =
        holdRepo.findById(holdId).orElseThrow(() -> new NotFoundException("Hold not found"));

    var eventId = hold.getEvent().getId();
    // lock event row for safe confirm under contention
    var event =
        eventRepo
            .findActiveByIdForUpdate(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));

    if (event.getStatus() != EventStatus.ACTIVE) throw new NotFoundException("Event not found");

    if (!hold.getUserId().equals(userId)) {
      throw new ConflictException("Hold does not belong to this user");
    }

    var now = LocalDateTime.now();
    if (hold.getStatus() == HoldStatus.CONFIRMED && hold.getConfirmedBookingId() != null) {
      return hold.getConfirmedBookingId();
    }
    if (hold.getStatus() != HoldStatus.ACTIVE) {
      throw new ConflictException("Hold is not active");
    }
    if (!hold.getExpiresAt().isAfter(now)) {
      hold.setStatus(HoldStatus.EXPIRED);
      holdRepo.save(hold);
      throw new ConflictException("Hold has expired");
    }

    // Enforce user-event double booking rule before creating booking.
    if (bookingRepo.findConfirmedByUserAndEvent(userId, eventId).isPresent()) {
      throw new ConflictException("User already has a confirmed booking for this event");
    }

    var booking = new Booking();
    booking.setEvent(event);
    booking.setUserId(userId);
    booking.setQuantity(hold.getQuantity());
    var saved = bookingRepo.save(booking);

    hold.markConfirmed(saved.getId());
    holdRepo.save(hold);

    evictAvailability(eventId);
    return saved.getId();
  }

  @Transactional
  public void cancelBooking(long bookingId, String userId) {
    var booking =
        bookingRepo.findById(bookingId).orElseThrow(() -> new NotFoundException("Booking not found"));
    if (!booking.getUserId().equals(userId)) {
      throw new ConflictException("Booking does not belong to this user");
    }
    booking.cancel();
    bookingRepo.save(booking);
    evictAvailability(booking.getEvent().getId());
  }

  private void evictAvailability(long eventId) {
    var cache = cacheManager.getCache(Config.AVAILABILITY_CACHE);
    if (cache != null) cache.evict(eventId);
  }
}

