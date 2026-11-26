package com.stockiq.inventory.controller;

import com.stockiq.inventory.common.enums.HttpStatusCodeEnum;
import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import com.stockiq.inventory.common.response.user.CreateUserResponse;
import com.stockiq.inventory.common.response.user.LoginResponse;
import com.stockiq.inventory.common.response.user.ProfileUpdateResponse;
import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.dto.LoginDto;
import com.stockiq.inventory.dto.UserDto;
import com.stockiq.inventory.dto.UpdateProfileDto;
import com.stockiq.inventory.dto.ResetPasswordDto;
import com.stockiq.inventory.dto.ForgotPasswordRequestDto;
import com.stockiq.inventory.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private static final String MAIN_LOG_KEY = "AUTH_";
  private static final String X_CORRELATION_ID = "X-Correlation-Id";
  private static final String INIT_LOG = "init_log";
  private static final String CONTROLLER = "controller";

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * Register a new user.
   *
   * @param userDto UserDto
   * @param correlationId String
   * @return ResponseEntity<CreateUserResponse>
   */
  @PostMapping("/register")
  public ResponseEntity<CreateUserResponse> register(
      @RequestBody @Valid UserDto userDto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    String logKey = MAIN_LOG_KEY + "REGISTER";

    ActivityLog.logInfo(correlationId, logKey, CONTROLLER, INIT_LOG);

    return ResponseEntity.status(HttpStatusCode.valueOf(HttpStatusCodeEnum.CREATE.getValue()))
        .body(authService.register(correlationId, logKey, userDto));
  }

  /**
   * Authenticate user and generate JWT token.
   *
   * @param loginDto LoginDto
   * @param correlationId String
   * @return ResponseEntity<LoginResponse>
   */
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @RequestBody @Valid LoginDto loginDto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    String logKey = MAIN_LOG_KEY + "LOGIN";

    ActivityLog.logInfo(correlationId, logKey, CONTROLLER, INIT_LOG);

    return ResponseEntity.status(HttpStatusCode.valueOf(HttpStatusCodeEnum.OK.getValue()))
        .body(authService.login(correlationId, logKey, loginDto));
  }

  /**
   * Get current authenticated user profile from JWT token.
   *
   * @param correlationId String
   * @return ResponseEntity<CreateUserResponse>
   */
  @GetMapping("/me")
  public ResponseEntity<CreateUserResponse> getCurrentUser(
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    String logKey = MAIN_LOG_KEY + "ME";

    ActivityLog.logInfo(correlationId, logKey, CONTROLLER, INIT_LOG);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String email =
        (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : null;

    if (email == null) {
      return ResponseEntity.status(
              HttpStatusCode.valueOf(HttpStatusCodeEnum.UNAUTHORIZED.getValue()))
          .build();
    }

    return ResponseEntity.status(HttpStatusCode.valueOf(HttpStatusCodeEnum.OK.getValue()))
        .body(authService.getCurrentUser(correlationId, logKey, email));
  }

  /**
   * Update the profile details of the currently authenticated user.
   *
   * @param updateDto UpdateProfileDto
   * @param correlationId String
   * @return ResponseEntity<ProfileUpdateResponse>
   */
  @PutMapping("/me")
  public ResponseEntity<ProfileUpdateResponse> updateProfile(
      @RequestBody @Valid UpdateProfileDto updateDto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    String logKey = MAIN_LOG_KEY + "UPDATE_PROFILE";

    ActivityLog.logInfo(correlationId, logKey, CONTROLLER, INIT_LOG);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String currentUserEmail =
        (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : null;

    if (currentUserEmail == null) {
      return ResponseEntity.status(
              HttpStatusCode.valueOf(HttpStatusCodeEnum.UNAUTHORIZED.getValue()))
          .build();
    }

    return ResponseEntity.status(HttpStatusCode.valueOf(HttpStatusCodeEnum.OK.getValue()))
        .body(authService.updateProfile(correlationId, logKey, currentUserEmail, updateDto));
  }

  /**
   * Step 1: Request password reset link / token by providing account email.
   * Returns a generic anti-enumeration response to prevent email discovery.
   *
   * @param requestDto ForgotPasswordRequestDto
   * @param correlationId String
   * @return ResponseEntity<Map<String, String>>
   */
  @PostMapping("/forgot-password")
  public ResponseEntity<Map<String, String>> forgotPassword(
      @RequestBody @Valid ForgotPasswordRequestDto requestDto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    String logKey = MAIN_LOG_KEY + "FORGOT_PASSWORD";

    ActivityLog.logInfo(correlationId, logKey, CONTROLLER, INIT_LOG);

    String devToken = authService.requestPasswordReset(correlationId, logKey, requestDto);

    Map<String, String> responseBody =
        (devToken != null && !devToken.isEmpty())
            ? Map.of(
                "message",
                "If the email exists, a password reset token has been dispatched.",
                "resetToken",
                devToken)
            : Map.of(
                "message",
                "If the email exists, a password reset token has been dispatched.");

    return ResponseEntity.ok(responseBody);
  }

  /**
   * Step 2: Confirm password reset using the cryptographically verified reset token.
   *
   * @param resetDto ResetPasswordDto
   * @param correlationId String
   * @return ResponseEntity<Map<String, String>>
   */
  @PostMapping("/reset-password")
  public ResponseEntity<Map<String, String>> resetPassword(
      @RequestBody @Valid ResetPasswordDto resetDto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    String logKey = MAIN_LOG_KEY + "RESET_PASSWORD";

    ActivityLog.logInfo(correlationId, logKey, CONTROLLER, INIT_LOG);

    authService.resetPassword(correlationId, logKey, resetDto);

    return ResponseEntity.ok(Map.of("message", "Password has been successfully reset."));
  }
}
