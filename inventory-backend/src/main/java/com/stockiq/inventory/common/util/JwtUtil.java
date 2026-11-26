package com.stockiq.inventory.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  private final String secret;
  private final long expiration;
  private SecretKey signingKey;

  public JwtUtil(
      @Value("${jwt.secret:}") String secret,
      @Value("${jwt.expiration:86400000}") long expiration) {
    this.secret = secret;
    this.expiration = expiration;
  }

  @PostConstruct
  public void init() {
    if (secret == null || secret.trim().isEmpty()) {
      throw new IllegalStateException(
          "JWT signing secret is not configured. Please set the 'JWT_SECRET' environment variable "
              + "or 'jwt.secret' application property with a secure secret (minimum 256-bit / 32 characters).");
    }
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalStateException(
          "JWT signing secret is too short. It must be at least 256 bits (32 bytes / characters) long.");
    }
    this.signingKey = Keys.hmacShaKeyFor(keyBytes);
  }

  private SecretKey getSigningKey() {
    return this.signingKey;
  }

  /**
   * Generate a signed JWT token containing email, role, and userId claims.
   *
   * @param email  String
   * @param role   String
   * @param userId long
   * @return String JWT token
   */
  public String generateToken(String email, String role, long userId) {
    return Jwts.builder()
        .subject(email)
        .claim("role", role)
        .claim("userId", userId)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(getSigningKey())
        .compact();
  }

  /**
   * Extract email (subject) from JWT token.
   *
   * @param token String
   * @return String email
   */
  public String extractEmail(String token) {
    return parseClaims(token).getSubject();
  }

  /**
   * Extract userId claim from JWT token.
   *
   * @param token String
   * @return Long userId
   */
  public Long extractUserId(String token) {
    Object userId = parseClaims(token).get("userId");
    if (userId instanceof Number) {
      return ((Number) userId).longValue();
    }
    return null;
  }

  /**
   * Extract role claim from JWT token.
   *
   * @param token String
   * @return String role
   */
  public String extractRole(String token) {
    return parseClaims(token).get("role", String.class);
  }

  /**
   * Validate token — parses and checks expiration.
   *
   * @param token String
   * @return boolean true if valid
   */
  public boolean validateToken(String token) {
    try {
      Claims claims = parseClaims(token);
      return claims.getExpiration().after(new Date());
    } catch (Exception e) {
      return false;
    }
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
