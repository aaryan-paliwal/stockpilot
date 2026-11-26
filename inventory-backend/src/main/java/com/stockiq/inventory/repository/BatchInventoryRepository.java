package com.stockiq.inventory.repository;

import com.stockiq.inventory.dto.BatchInventoryDto;
import com.stockiq.inventory.entity.BatchInventory;
import java.util.List;
import java.util.Optional;

public interface BatchInventoryRepository {
  long save(String correlationId, String logKey, BatchInventory inv) throws Exception;
  int updateQty(String correlationId, String logKey, long batchId, long warehouseId, int newQty) throws Exception;
  Optional<BatchInventory> findByBatchAndWarehouse(String correlationId, String logKey, long batchId, long warehouseId) throws Exception;
  Optional<BatchInventory> findByBatchAndWarehouseForUpdate(String correlationId, String logKey, long batchId, long warehouseId) throws Exception;
  List<BatchInventoryDto> findByWarehouseAndProduct(String correlationId, String logKey, long warehouseId, long productId) throws Exception;
  List<BatchInventoryDto> findFefoCandidates(String correlationId, String logKey, long warehouseId, long productId) throws Exception;
  int getAggregateBatchStock(String correlationId, String logKey, long batchId) throws Exception;
}

