package com.stockiq.inventory.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseInventoryDto {
  private Long id;
  private Long warehouseId;
  private String warehouseCode;
  private String warehouseName;
  private Long productId;
  private String productSku;
  private String productName;
  private Integer qty;
  private String updatedAt;
}

