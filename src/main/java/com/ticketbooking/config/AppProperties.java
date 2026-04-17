package com.ticketbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Holds holds, Cache cache, Security security) {
  public record Holds(int ttlMinutes, int maxQuantityPerHold) {}

  public record Cache(int availabilityTtlSeconds) {}

  public record Security(Jwt jwt) {
    public record Jwt(String issuer, int accessTokenTtlMinutes, String secret) {}
  }
}

