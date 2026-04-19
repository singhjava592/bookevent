package com.ticketbooking.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class BookingDtos {
  public record HoldSeatsRequest(@NotBlank String userId, @Min(1) int quantity) {}

  public record HoldSeatsResponse(String holdId, LocalDateTime expiresAt) {}

  public record ConfirmBookingRequest(@NotBlank String userId) {}

  public record ConfirmBookingResponse(long bookingId) {}

  public record CancelBookingRequest(@NotBlank String userId) {}

  public record CancelBookingResponse(long bookingId, String message) {}

  public record BookingResponse(
      long bookingId,
      long eventId,
      String userId,
      int quantity,
      String status,
      LocalDateTime createdAt,
      LocalDateTime canceledAt) {}
}

