package com.stockiq.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseTransferDto {
  private Long id;

  @NotNull(message = "Source warehouse is required")
  private Long sourceWarehouseId;
  private String sourceWarehouseCode;
  private String sourceWarehouseName;

  @NotNull(message = "Destination warehouse is required")
  private Long destWarehouseId;
  private String destWarehouseCode;
  private String destWarehouseName;

  @NotNull(message = "Product ID is required")
  private Long productId;
  private String productSku;
  private String productName;

  @NotNull(message = "Quantity is required")
  @Min(value = 1, message = "Transfer quantity must be at least 1")
  private Integer qty;

  private String status; // INITIATED, APPROVED, COMPLETED, CANCELLED
  private String referenceNote;
  private String initiatingUser;
  private String approvingUser;

  private String createdAt;
  private String approvedAt;
  private String completedAt;
  private String cancelledAt;
}
