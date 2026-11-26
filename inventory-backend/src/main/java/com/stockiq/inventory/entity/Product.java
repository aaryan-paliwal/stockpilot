package com.stockiq.inventory.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
  private long id;
  private Long userId;
  private String sku;
  private String name;
  private String description;
  private Long categoryId;
  private Long supplierId;
  private String uom;
  private BigDecimal costPrice;
  private BigDecimal sellingPrice;
  private int qty;
  private int reorderPoint;
  private int status;
  private boolean batchTracked;
  private String createdBy;
  private String updatedBy;
  private Timestamp createdAt;
  private Timestamp updatedAt;
}
