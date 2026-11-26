package com.stockiq.inventory.service;

import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import com.stockiq.inventory.dto.WarehouseTransferDto;
import java.util.List;

public interface WarehouseTransferService {
  WarehouseTransferDto initiateTransfer(String correlationId, String logKey, WarehouseTransferDto dto) throws GeneralStatusCodeErrorException;
  WarehouseTransferDto approveTransfer(String correlationId, String logKey, long transferId) throws GeneralStatusCodeErrorException;
  WarehouseTransferDto completeTransfer(String correlationId, String logKey, long transferId) throws GeneralStatusCodeErrorException;
  WarehouseTransferDto cancelTransfer(String correlationId, String logKey, long transferId, String reason) throws GeneralStatusCodeErrorException;
  WarehouseTransferDto findById(String correlationId, String logKey, long id) throws GeneralStatusCodeErrorException;
  List<WarehouseTransferDto> findAll(String correlationId, String logKey, String status) throws GeneralStatusCodeErrorException;
}
