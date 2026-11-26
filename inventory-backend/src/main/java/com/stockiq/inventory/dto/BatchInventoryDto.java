package com.stockiq.inventory.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchInventoryDto {
  private Long id;
  private Long batchId;
  private String batchNumber;
  private Long warehouseId;
  private String warehouseCode;
  private String warehouseName;
  private Long productId;
  private String productSku;
  private String productName;
  private Integer qty;
  private String expiryDate;
  private String updatedAt;
}

