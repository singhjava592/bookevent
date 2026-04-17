package com.ticketbooking.api.dto;

import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

public class BookingDtos {
  public record HoldSeatsRequest(@Min(1) int quantity) {}

  public record HoldSeatsResponse(String holdId, LocalDateTime expiresAt) {}

  public record ConfirmBookingResponse(long bookingId) {}

  public record BookingResponse(
      long bookingId,
      long eventId,
      String userId,
      int quantity,
      String status,
      LocalDateTime createdAt,
      LocalDateTime canceledAt) {}
}

