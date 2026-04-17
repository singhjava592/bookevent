package com.ticketbooking.api.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  public record LoginResponse(String accessToken, String tokenType) {}

  public record LogoutResponse(String message) {}
}

