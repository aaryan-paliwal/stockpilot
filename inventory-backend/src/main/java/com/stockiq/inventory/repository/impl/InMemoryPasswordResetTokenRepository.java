package com.stockiq.inventory.repository.impl;

import com.stockiq.inventory.entity.PasswordResetToken;
import com.stockiq.inventory.repository.PasswordResetTokenRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("test")
public class InMemoryPasswordResetTokenRepository implements PasswordResetTokenRepository {

  private final Map<String, PasswordResetToken> store = new ConcurrentHashMap<>();

  @Override
  public void save(PasswordResetToken token) {
    cleanExpired();
    store.put(token.getTokenHash(), token);
  }

  @Override
  public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
    cleanExpired();
    return Optional.ofNullable(store.get(tokenHash));
  }

  @Override
  public void markAsUsed(String tokenHash) {
    PasswordResetToken token = store.get(tokenHash);
    if (token != null) {
      token.setUsed(true);
    }
  }

  @Override
  public void invalidateAllForEmail(String email) {
    if (email == null) return;
    store.values().removeIf(t -> email.equalsIgnoreCase(t.getEmail()));
  }

  private void cleanExpired() {
    Instant now = Instant.now();
    store.entrySet().removeIf(entry -> entry.getValue().getExpiresAt().isBefore(now));
  }
}

