package com.stockiq.inventory.repository.impl;

import com.stockiq.inventory.entity.PasswordResetToken;
import com.stockiq.inventory.repository.PasswordResetTokenRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL persistent implementation of PasswordResetTokenRepository.
 * Manages secure storage of token hashes with atomic invalidation.
 */
@Repository
@Primary
public class JdbcPasswordResetTokenRepository implements PasswordResetTokenRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcPasswordResetTokenRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private static final RowMapper<PasswordResetToken> ROW_MAPPER =
      (ResultSet rs, int rowNum) ->
          PasswordResetToken.builder()
              .tokenHash(rs.getString("token_hash"))
              .email(rs.getString("email"))
              .expiresAt(rs.getTimestamp("expires_at").toInstant())
              .used(rs.getBoolean("used"))
              .build();

  @Override
  public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
    String sql =
        """
        SELECT token_hash, email, expires_at, used
        FROM password_reset_tokens
        WHERE token_hash = :tokenHash
        """;

    List<PasswordResetToken> list =
        jdbcTemplate.query(sql, Map.of("tokenHash", tokenHash), ROW_MAPPER);

    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  @Transactional
  public void save(PasswordResetToken token) {
    // Invalidate existing unused tokens for this email to prevent multiple valid tokens
    invalidateAllForEmail(token.getEmail());

    String sql =
        """
        INSERT INTO password_reset_tokens (token_hash, email, expires_at, used, created_at)
        VALUES (:tokenHash, :email, :expiresAt, :used, :createdAt)
        """;

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("tokenHash", token.getTokenHash())
            .addValue("email", token.getEmail().trim().toLowerCase())
            .addValue("expiresAt", Timestamp.from(token.getExpiresAt()))
            .addValue("used", token.isUsed())
            .addValue("createdAt", Timestamp.from(Instant.now()));

    jdbcTemplate.update(sql, params);
  }

  @Override
  @Transactional
  public void markAsUsed(String tokenHash) {
    String sql =
        """
        UPDATE password_reset_tokens
        SET used = TRUE
        WHERE token_hash = :tokenHash AND used = FALSE
        """;

    jdbcTemplate.update(sql, Map.of("tokenHash", tokenHash));
  }

  @Override
  @Transactional
  public void invalidateAllForEmail(String email) {
    String sql =
        """
        UPDATE password_reset_tokens
        SET used = TRUE
        WHERE email = :email AND used = FALSE
        """;

    jdbcTemplate.update(sql, Map.of("email", email.trim().toLowerCase()));
  }
}
