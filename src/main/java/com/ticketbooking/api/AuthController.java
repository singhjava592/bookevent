package com.ticketbooking.api;

import com.ticketbooking.api.dto.AuthDtos.*;
import com.ticketbooking.security.JwtService;
import com.ticketbooking.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

/**
 * Flow/design: Stateless JWT auth; controllers rely on `Principal`/roles from SecurityContext.
 * Endpoints: `/auth/login` issues token; `/auth/logout` revokes current token (in-memory blacklist).
 */
@RestController
@RequestMapping("/auth")
public class AuthController {
  private final AuthenticationManager authManager;
  private final JwtService jwtService;
  private final TokenBlacklist blacklist;

  public AuthController(AuthenticationManager authManager, JwtService jwtService, TokenBlacklist blacklist) {
    this.authManager = authManager;
    this.jwtService = jwtService;
    this.blacklist = blacklist;
  }

  // POST /auth/login: Validate credentials and issue JWT access token.
  // Response: { accessToken, tokenType="Bearer" }.
  @PostMapping("/login")
  @ResponseStatus(HttpStatus.OK)
  public LoginResponse login(@Valid @RequestBody LoginRequest req) {
    Authentication auth =
        authManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
    var roles =
        auth.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(r -> r.startsWith("ROLE_"))
            .map(r -> r.substring("ROLE_".length()))
            .toList();
    var token = jwtService.issueAccessToken(auth.getName(), roles);
    return new LoginResponse(token, "Bearer");
  }

  // POST /auth/logout: Revoke the current Bearer token (best-effort for single-node).
  // Note: Stateless JWT logout requires a blacklist; for multi-node use Redis/shared store.
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.OK)
  public LogoutResponse logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return new LogoutResponse("Logged out");
    }
    var token = authorization.substring("Bearer ".length()).trim();
    try {
      Claims claims = jwtService.parseAndValidate(token);
      var exp = claims.getExpiration() == null ? Instant.now() : claims.getExpiration().toInstant();
      blacklist.revoke(token, exp);
    } catch (Exception ignored) {
      // If token is invalid/expired, treat as logged out.
    }
    return new LogoutResponse("Logged out");
  }
}

