package com.stockiq.inventory.common.util;

import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

  private SecurityUtils() {
    // Private constructor to prevent instantiation
  }

  /**
   * Safely retrieves the authenticated user's email from the Security Context.
   *
   * @return String representing the user's email, or null if not authenticated.
   */
  public static String getCurrentUserEmail() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
      return authentication.getName();
    }
    return null;
  }

  /**
   * Safely retrieves the authenticated user's database ID from the Security Context.
   *
   * @return String representing the user's database ID, or null if not authenticated.
   */
  public static String getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      Object principal = authentication.getPrincipal();
      if (principal instanceof CustomUserPrincipal) {
        return String.valueOf(((CustomUserPrincipal) principal).getUserId());
      }
    }
    return null;
  }

  /**
   * Safely retrieves the authenticated user's database ID as a Long from the Security Context.
   *
   * @return Long representing the user's database ID, or null if not authenticated.
   */
  public static Long getCurrentUserIdAsLong() {
    String userIdStr = getCurrentUserId();
    if (userIdStr != null) {
      try {
        return Long.valueOf(userIdStr);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Enforces that an authenticated user identity exists and returns the user ID.
   * Throws GeneralStatusCodeErrorException (401 Unauthorized) if no valid authenticated user is present.
   *
   * @return Long authenticated user ID
   * @throws GeneralStatusCodeErrorException when not authenticated
   */
  public static Long requireAuthenticatedUserId() throws GeneralStatusCodeErrorException {
    Long userId = getCurrentUserIdAsLong();
    if (userId == null || userId <= 0) {
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Full authentication is required to perform this operation",
              "Unauthorized",
              HttpStatus.UNAUTHORIZED,
              401,
              "AUTH_401",
              java.time.LocalDateTime.now().toString(),
              null));
    }
    return userId;
  }

  /**
   * Enforces that an authenticated user email exists and returns the email.
   * Throws GeneralStatusCodeErrorException (401 Unauthorized) if no valid authenticated email is present.
   *
   * @return String authenticated user email
   * @throws GeneralStatusCodeErrorException when not authenticated
   */
  public static String requireAuthenticatedUserEmail() throws GeneralStatusCodeErrorException {
    String email = getCurrentUserEmail();
    if (email == null || email.trim().isEmpty()) {
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Full authentication is required to perform this operation",
              "Unauthorized",
              HttpStatus.UNAUTHORIZED,
              401,
              "AUTH_401",
              java.time.LocalDateTime.now().toString(),
              null));
    }
    return email;
  }
}
