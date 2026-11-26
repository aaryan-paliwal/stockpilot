package com.stockiq.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductBatchDto {
  private Long id;
  private Long userId;

  @NotNull(message = "Product ID is required")
  private Long productId;
  private String productSku;
  private String productName;

  @NotBlank(message = "Batch number is required")
  private String batchNumber;

  private String mfgDate;
  private String expiryDate;

  @DecimalMin(value = "0.0", inclusive = true, message = "Cost price cannot be negative")
  @Builder.Default
  private BigDecimal costPrice = BigDecimal.ZERO;

  private Boolean costEstimated;

  @Builder.Default
  private String status = "ACTIVE";

  private Integer totalQty;

  private String createdAt;
  private String updatedAt;
}

