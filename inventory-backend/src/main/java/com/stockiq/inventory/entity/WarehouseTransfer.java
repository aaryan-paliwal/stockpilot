package com.stockiq.inventory.entity;

import java.sql.Timestamp;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseTransfer {
  private long id;
  private Long userId;
  private long sourceWarehouseId;
  private long destWarehouseId;
  private long productId;
  private int qty;
  private String status; // INITIATED, APPROVED, COMPLETED, CANCELLED
  private String referenceNote;
  private String initiatingUser;
  private String approvingUser;
  private Timestamp createdAt;
  private Timestamp approvedAt;
  private Timestamp completedAt;
  private Timestamp cancelledAt;
}
