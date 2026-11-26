package com.stockiq.inventory.repository;

import com.stockiq.inventory.dto.ProductBatchDto;
import com.stockiq.inventory.entity.ProductBatch;
import java.util.List;
import java.util.Optional;

public interface ProductBatchRepository {
  long save(String correlationId, String logKey, ProductBatch batch) throws Exception;
  int update(String correlationId, String logKey, ProductBatch batch) throws Exception;
  Optional<ProductBatch> findById(String correlationId, String logKey, long id) throws Exception;
  Optional<ProductBatch> findByProductAndBatchNumber(String correlationId, String logKey, long productId, String batchNumber) throws Exception;
  List<ProductBatchDto> findByProductId(String correlationId, String logKey, long productId) throws Exception;
  List<ProductBatchDto> findExpiringBatches(String correlationId, String logKey, int daysThreshold) throws Exception;
}

