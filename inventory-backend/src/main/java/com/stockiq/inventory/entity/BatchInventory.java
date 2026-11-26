package com.stockiq.inventory.entity;

import java.sql.Timestamp;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchInventory {
  private long id;
  private Long userId;
  private long batchId;
  private long warehouseId;
  private long productId;
  private int qty;
  private Timestamp createdAt;
  private Timestamp updatedAt;
}

