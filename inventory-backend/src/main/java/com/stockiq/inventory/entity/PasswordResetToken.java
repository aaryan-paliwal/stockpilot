package com.stockiq.inventory.entity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

  private String tokenHash;
  private String email;
  private Instant expiresAt;
  private boolean used;
}

