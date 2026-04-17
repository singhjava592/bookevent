package com.ticketbooking.security;

import com.ticketbooking.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  public static final String CLAIM_ROLES = "roles";

  private final AppProperties props;
  private final SecretKey key;

  public JwtService(AppProperties props) {
    this.props = props;
    var secret = props.security().jwt().secret();
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String issueAccessToken(String username, List<String> roles) {
    var now = Instant.now();
    var exp = now.plus(props.security().jwt().accessTokenTtlMinutes(), ChronoUnit.MINUTES);
    return Jwts.builder()
        .issuer(props.security().jwt().issuer())
        .subject(username)
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .claim(CLAIM_ROLES, roles)
        .signWith(key)
        .compact();
  }

  public Claims parseAndValidate(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .requireIssuer(props.security().jwt().issuer())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}

