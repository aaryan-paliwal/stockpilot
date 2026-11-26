package com.stockiq.inventory.common.response.stock;

import com.stockiq.inventory.common.enums.stock.MovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogMovementResponse {
  private long id;
  private Long warehouseId;
  private String warehouseCode;
  private MovementType type;
  private String product;
  private Long productId;
  private String sku;
  private Integer qty; // Quantity moved or adjusted
  private Integer previousQty;
  private Integer resultingQty;
  private Integer deltaQty; // Signed change: resultingQty - previousQty
  private String reasonReference;
  private String date;
  private String user;
}
