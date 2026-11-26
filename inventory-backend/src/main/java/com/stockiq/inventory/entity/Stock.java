package com.stockiq.inventory.entity;

import java.sql.Timestamp;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {
  private long id;
  private Long userId;
  private Long warehouseId;
  private Long batchId;
  private int type; // 1 = IN / STOCK_IN, 0 = OUT / STOCK_OUT, 2 = ADJUSTMENT
  private String typeName; // 'STOCK_IN', 'STOCK_OUT', 'ADJUSTMENT'
  private long productId;
  private int qty;
  private int previousQty;
  private int resultingQty;
  private String reasonReference;
  private String createdBy;
  private Timestamp createdAt;
}
