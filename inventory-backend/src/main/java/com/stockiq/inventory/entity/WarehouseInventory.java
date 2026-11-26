package com.stockiq.inventory.entity;

import java.sql.Timestamp;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseInventory {
  private long id;
  private Long userId;
  private long warehouseId;
  private long productId;
  private int qty;
  private Timestamp createdAt;
  private Timestamp updatedAt;
}

