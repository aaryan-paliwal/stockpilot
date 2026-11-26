package com.stockiq.inventory.dto;

import com.stockiq.inventory.common.enums.product.ProductStatus;
import com.stockiq.inventory.common.enums.stock.StockLevel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDto {
  private Long id;
  private Long userId;

  private String sku;

  @NotBlank(message = "Product name is required")
  private String name;

  private String description;

  @NotBlank(message = "Category is required")
  private String category;

  private Long supplierId;
  private String supplierName;

  @Builder.Default
  private String uom = "PCS";

  @DecimalMin(value = "0.0", inclusive = true, message = "Cost price cannot be negative")
  @Builder.Default
  private BigDecimal costPrice = BigDecimal.ZERO;

  @DecimalMin(value = "0.0", inclusive = true, message = "Selling price cannot be negative")
  @Builder.Default
  private BigDecimal sellingPrice = BigDecimal.ZERO;

  @NotNull(message = "Quantity is required")
  @Min(value = 0, message = "Quantity cannot be negative")
  private Integer qty;

  @NotNull(message = "Reorder point is required")
  @Min(value = 0, message = "Reorder point cannot be negative")
  private Integer reorder;

  private ProductStatus status;
  private StockLevel stockLevel;
  private Boolean batchTracked;

  private String createdAt;
  private String createdBy;
  private String updatedAt;
  private String updatedBy;
}
