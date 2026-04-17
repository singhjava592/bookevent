package com.ticketbooking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final TokenBlacklist blacklist;

  public JwtAuthFilter(JwtService jwtService, TokenBlacklist blacklist) {
    this.jwtService = jwtService;
    this.blacklist = blacklist;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    var token = header.substring("Bearer ".length()).trim();
    if (token.isEmpty() || blacklist.isRevoked(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      Claims claims = jwtService.parseAndValidate(token);
      var username = claims.getSubject();
      if (username == null || username.isBlank()) {
        filterChain.doFilter(request, response);
        return;
      }

      @SuppressWarnings("unchecked")
      var roles = (List<String>) claims.get(JwtService.CLAIM_ROLES, List.class);
      Collection<? extends GrantedAuthority> authorities =
          roles == null
              ? List.of()
              : roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();

      var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
      auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(auth);
    } catch (JwtException ex) {
      // Invalid token: do not authenticate; allow security chain to reject as needed.
      SecurityContextHolder.clearContext();
    }

    filterChain.doFilter(request, response);
  }
}

