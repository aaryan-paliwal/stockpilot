package com.stockiq.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordDto {

  private String email;

  @NotBlank(message = "Reset token is required")
  private String token;

  @NotBlank(message = "New password is required")
  private String newPassword;
}
