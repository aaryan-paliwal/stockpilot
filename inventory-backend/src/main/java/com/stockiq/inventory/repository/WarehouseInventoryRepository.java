package com.stockiq.inventory.repository;

import com.stockiq.inventory.dto.WarehouseInventoryDto;
import com.stockiq.inventory.entity.WarehouseInventory;
import java.util.List;
import java.util.Optional;

public interface WarehouseInventoryRepository {
  long save(String correlationId, String logKey, WarehouseInventory inv) throws Exception;
  int updateQty(String correlationId, String logKey, long warehouseId, long productId, int newQty) throws Exception;
  Optional<WarehouseInventory> findByWarehouseAndProduct(String correlationId, String logKey, long warehouseId, long productId) throws Exception;
  Optional<WarehouseInventory> findByWarehouseAndProductForUpdate(String correlationId, String logKey, long warehouseId, long productId) throws Exception;
  List<WarehouseInventoryDto> findByWarehouseId(String correlationId, String logKey, long warehouseId) throws Exception;
  List<WarehouseInventoryDto> findByProductId(String correlationId, String logKey, long productId) throws Exception;
  int getAggregateProductStock(String correlationId, String logKey, long productId) throws Exception;
}

