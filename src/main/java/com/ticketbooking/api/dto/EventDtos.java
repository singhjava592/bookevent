package com.ticketbooking.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class EventDtos {
  public record CreateEventRequest(
      @NotBlank String name,
      @NotNull LocalDateTime eventDate,
      @NotBlank String location,
      @Min(0) int totalSeats) {}

  public record UpdateEventRequest(
      @NotBlank String name,
      @NotNull LocalDateTime eventDate,
      @NotBlank String location,
      @Min(0) int totalSeats) {}

  public record EventResponse(
      long id, String name, LocalDateTime eventDate, String location, int totalSeats) {}
}

