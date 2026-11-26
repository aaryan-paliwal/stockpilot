package com.stockiq.inventory.repository;

import com.stockiq.inventory.entity.Warehouse;
import java.util.List;
import java.util.Optional;

public interface WarehouseRepository {
  long save(String correlationId, String logKey, Warehouse warehouse) throws Exception;
  int update(String correlationId, String logKey, Warehouse warehouse) throws Exception;
  Optional<Warehouse> findById(String correlationId, String logKey, long id) throws Exception;
  Optional<Warehouse> findByCode(String correlationId, String logKey, String code) throws Exception;
  List<Warehouse> findAll(String correlationId, String logKey) throws Exception;
  int deactivate(String correlationId, String logKey, long id) throws Exception;
  Optional<Warehouse> findOrCreateDefaultWarehouse(String correlationId, String logKey, Long userId) throws Exception;
}

