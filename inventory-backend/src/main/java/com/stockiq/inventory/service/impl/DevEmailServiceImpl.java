package com.stockiq.inventory.service.impl;

import com.stockiq.inventory.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DevEmailServiceImpl implements EmailService {

  private static final Logger log = LoggerFactory.getLogger(DevEmailServiceImpl.class);

  private final boolean devMode;

  public DevEmailServiceImpl(@Value("${app.auth.dev-mode:false}") boolean devMode) {
    this.devMode = devMode;
  }

  @Override
  public void sendPasswordResetEmail(String recipientEmail, String rawToken) {
    if (devMode) {
      log.info(
          "[DEV MODE ONLY] Password reset requested for {}. Reset token: {}",
          recipientEmail,
          rawToken);
    } else {
      log.info(
          "Password reset requested for recipient. External email service not configured.");
    }
  }

  @Override
  public boolean isDevMode() {
    return devMode;
  }
}

