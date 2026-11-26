package com.stockiq.inventory.service;

public interface EmailService {

  /**
   * Dispatches a password reset link or token.
   *
   * @param recipientEmail destination email address
   * @param rawToken unhashed one-time reset token
   */
  void sendPasswordResetEmail(String recipientEmail, String rawToken);

  /**
   * Indicates whether dev mode is enabled.
   *
   * @return true if dev mode is enabled
   */
  boolean isDevMode();
}


