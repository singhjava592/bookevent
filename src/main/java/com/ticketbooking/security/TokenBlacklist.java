package com.ticketbooking.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Logout support for JWT by keeping a short-lived blacklist in-memory.
 * Works for single-node deployments; for multi-node use Redis or shared store.
 */
@Component
public class TokenBlacklist {
  private final Cache<String, Instant> cache =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(24)).maximumSize(200_000).build();

  public void revoke(String token, Instant expiresAt) {
    // TTL is handled by cache's expireAfterWrite; store expiry so we can ignore obviously old tokens.
    cache.put(token, expiresAt);
  }

  public boolean isRevoked(String token) {
    var exp = cache.getIfPresent(token);
    if (exp == null) return false;
    if (exp.isBefore(Instant.now())) {
      cache.invalidate(token);
      return false;
    }
    return true;
  }
}

