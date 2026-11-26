package com.stockiq.inventory.dto;

import com.stockiq.inventory.common.enums.stock.MovementType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDto {
  private Long id;

  private Long warehouseId;
  private String warehouseCode;
  private String warehouseName;

  private Long productId;
  private String sku;

  @NotNull(message = "Movement type is required")
  private MovementType type; // 'IN', 'OUT', 'ADJUSTMENT' or 'STOCK_IN', 'STOCK_OUT'

  private String product; // Product name

  @NotNull(message = "Quantity is required")
  @Min(value = 1, message = "Quantity must be at least 1")
  private Integer qty;

  private Integer previousQty;
  private Integer resultingQty;
  private Integer deltaQty;
  private String reasonReference;

  private String date; // Formatted date & time
  private String user; // Logged by user
}
