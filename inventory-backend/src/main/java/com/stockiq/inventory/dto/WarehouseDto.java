package com.stockiq.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseDto {
  private Long id;

  @NotBlank(message = "Warehouse code is required")
  @Size(max = 50, message = "Code must be at most 50 characters")
  private String code;

  @NotBlank(message = "Warehouse name is required")
  @Size(max = 150, message = "Name must be at most 150 characters")
  private String name;

  private String description;
  private String address;
  private String city;
  private String state;
  private String postalCode;
  private Boolean active;

  private String createdAt;
  private String updatedAt;
}

