package com.stockiq.inventory.repository;

import com.stockiq.inventory.dto.WarehouseTransferDto;
import com.stockiq.inventory.entity.WarehouseTransfer;
import java.util.List;
import java.util.Optional;

public interface WarehouseTransferRepository {
  long save(String correlationId, String logKey, WarehouseTransfer transfer) throws Exception;
  int updateStatus(String correlationId, String logKey, long transferId, String status, String approvingUser) throws Exception;
  Optional<WarehouseTransfer> findById(String correlationId, String logKey, long id) throws Exception;
  Optional<WarehouseTransfer> findByIdForUpdate(String correlationId, String logKey, long id) throws Exception;
  List<WarehouseTransferDto> findAll(String correlationId, String logKey) throws Exception;
  List<WarehouseTransferDto> findByStatus(String correlationId, String logKey, String status) throws Exception;
}

