package com.stockiq.inventory.entity;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductBatch {
  private long id;
  private Long userId;
  private long productId;
  private String batchNumber;
  private Date mfgDate;
  private Date expiryDate;
  private BigDecimal costPrice;
  private boolean costEstimated;
  private String status; // 'ACTIVE', 'QUARANTINED', 'EXPIRED', 'DEPLETED', 'ARCHIVED'
  private Timestamp createdAt;
  private Timestamp updatedAt;
}

