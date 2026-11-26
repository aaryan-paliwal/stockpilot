package com.stockiq.inventory.service;

import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import com.stockiq.inventory.dto.WarehouseDto;
import com.stockiq.inventory.dto.WarehouseInventoryDto;
import java.util.List;

public interface WarehouseService {
  WarehouseDto create(String correlationId, String logKey, WarehouseDto dto) throws GeneralStatusCodeErrorException;
  WarehouseDto update(String correlationId, String logKey, long id, WarehouseDto dto) throws GeneralStatusCodeErrorException;
  WarehouseDto findById(String correlationId, String logKey, long id) throws GeneralStatusCodeErrorException;
  List<WarehouseDto> findAll(String correlationId, String logKey) throws GeneralStatusCodeErrorException;
  void deactivate(String correlationId, String logKey, long id) throws GeneralStatusCodeErrorException;
  List<WarehouseInventoryDto> getInventoryByWarehouse(String correlationId, String logKey, long warehouseId) throws GeneralStatusCodeErrorException;
  List<WarehouseInventoryDto> getInventoryByProduct(String correlationId, String logKey, long productId) throws GeneralStatusCodeErrorException;
}

