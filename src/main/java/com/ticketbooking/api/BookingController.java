package com.ticketbooking.api;

import com.ticketbooking.api.dto.BookingDtos.*;
import com.ticketbooking.repo.BookingRepository;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.service.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingController {
  /**
   * Flow/design: Hold -> Confirm -> (optional) Cancel; availability is computed as total - booked - active holds.
   * APIs are open; `userId` is provided explicitly for user-specific business rules.
   */
  private final BookingService bookingService;
  private final BookingRepository bookingRepo;

  public BookingController(BookingService bookingService, BookingRepository bookingRepo) {
    this.bookingService = bookingService;
    this.bookingRepo = bookingRepo;
  }

  // POST /events/{eventId}/holds: Create a 5-min temporary hold for N seats; returns holdId.
  // Prevents overbooking under contention via DB locking in service layer.
  @PostMapping("/events/{eventId}/holds")
  @ResponseStatus(HttpStatus.CREATED)
  public HoldSeatsResponse hold(
      @PathVariable long eventId, @Valid @RequestBody HoldSeatsRequest req) {
    var res = bookingService.holdSeats(eventId, req.userId(), req.quantity());
    return new HoldSeatsResponse(res.holdId(), res.expiresAt());
  }

  // POST /holds/{holdId}/confirm: Validate hold (owner + not expired) and create confirmed booking.
  // Idempotent: if hold already confirmed, returns existing bookingId.
  @PostMapping("/holds/{holdId}/confirm")
  @ResponseStatus(HttpStatus.CREATED)
  public ConfirmBookingResponse confirm(
      @PathVariable String holdId, @Valid @RequestBody ConfirmBookingRequest req) {
    var bookingId = bookingService.confirmHold(holdId, req.userId());
    return new ConfirmBookingResponse(bookingId);
  }

  // GET /bookings/{bookingId}: Fetch booking details.
  // This is open; no ownership filtering is applied.
  @GetMapping("/bookings/{bookingId}")
  public BookingResponse get(@PathVariable long bookingId) {
    var b =
        bookingRepo.findById(bookingId).orElseThrow(() -> new NotFoundException("Booking not found"));
    return new BookingResponse(
        b.getId(),
        b.getEvent().getId(),
        b.getUserId(),
        b.getQuantity(),
        b.getStatus().name(),
        b.getCreatedAt(),
        b.getCanceledAt());
  }

  // POST /bookings/{bookingId}/cancel: Soft-cancel a confirmed booking (audit trail).
  // Seats become available again immediately.
  @PostMapping("/bookings/{bookingId}/cancel")
  @ResponseStatus(HttpStatus.OK)
  public CancelBookingResponse cancel(
      @PathVariable long bookingId, @Valid @RequestBody CancelBookingRequest req) {
    bookingService.cancelBooking(bookingId, req.userId());
    return new CancelBookingResponse(
        bookingId, "Booking " + bookingId + " has been cancelled successfully.");
  }
}

