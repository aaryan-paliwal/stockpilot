package com.stockiq.inventory.repository;

import com.stockiq.inventory.entity.PasswordResetToken;
import java.util.Optional;

public interface PasswordResetTokenRepository {

  void save(PasswordResetToken token);

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  void markAsUsed(String tokenHash);

  void invalidateAllForEmail(String email);
}

