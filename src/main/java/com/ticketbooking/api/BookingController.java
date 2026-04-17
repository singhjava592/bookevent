package com.ticketbooking.api;

import com.ticketbooking.api.dto.BookingDtos.*;
import com.ticketbooking.repo.BookingRepository;
import com.ticketbooking.service.BookingService;
import com.ticketbooking.service.NotFoundException;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingController {
  /**
   * Flow/design: Hold -> Confirm -> (optional) Cancel; availability is computed as total - booked - active holds.
   * All endpoints require JWT (`Authorization: Bearer ...`) and USER role.
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
  @PreAuthorize("hasRole('USER')")
  public HoldSeatsResponse hold(
      @PathVariable long eventId, @Valid @RequestBody HoldSeatsRequest req, Principal principal) {
    var res = bookingService.holdSeats(eventId, principal.getName(), req.quantity());
    return new HoldSeatsResponse(res.holdId(), res.expiresAt());
  }

  // POST /holds/{holdId}/confirm: Validate hold (owner + not expired) and create confirmed booking.
  // Idempotent: if hold already confirmed, returns existing bookingId.
  @PostMapping("/holds/{holdId}/confirm")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('USER')")
  public ConfirmBookingResponse confirm(@PathVariable String holdId, Principal principal) {
    var bookingId = bookingService.confirmHold(holdId, principal.getName());
    return new ConfirmBookingResponse(bookingId);
  }

  // GET /bookings/{bookingId}: Fetch booking details for the authenticated user.
  // Uses 404 if booking not found or not owned (no information leakage).
  @GetMapping("/bookings/{bookingId}")
  @PreAuthorize("hasRole('USER')")
  public BookingResponse get(@PathVariable long bookingId, Principal principal) {
    var b =
        bookingRepo.findById(bookingId).orElseThrow(() -> new NotFoundException("Booking not found"));
    if (!b.getUserId().equals(principal.getName())) {
      throw new NotFoundException("Booking not found");
    }
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
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('USER')")
  public void cancel(@PathVariable long bookingId, Principal principal) {
    bookingService.cancelBooking(bookingId, principal.getName());
  }
}

