package com.stockiq.inventory.service;

import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import com.stockiq.inventory.dto.ProductDto;
import com.stockiq.inventory.dto.StockDto;
import java.util.List;

public interface StockService {

  /**
   * Log stock movement (STOCK_IN, STOCK_OUT, ADJUSTMENT) with pessimistic row locking.
   *
   * @param correlationId String
   * @param logKey String
   * @param movementDto StockDto
   * @param loggedByUserId String
   * @return StockDto
   * @throws GeneralStatusCodeErrorException if an error occurs during the operation
   */
  StockDto logMovement(
      String correlationId, String logKey, StockDto movementDto, String loggedByUserId)
      throws GeneralStatusCodeErrorException;

  /**
   * Get stock movement history.
   *
   * @param correlationId String
   * @param logKey String
   * @return List<StockDto>
   * @throws GeneralStatusCodeErrorException if an error occurs during the operation
   */
  List<StockDto> getHistory(String correlationId, String logKey)
      throws GeneralStatusCodeErrorException;

  /**
   * Get stock movement history for a specific product.
   *
   * @param correlationId String
   * @param logKey String
   * @param productId long
   * @return List<StockDto>
   * @throws GeneralStatusCodeErrorException if an error occurs
   */
  List<StockDto> getHistoryByProduct(String correlationId, String logKey, long productId)
      throws GeneralStatusCodeErrorException;

  /**
   * Get low stock alerts.
   *
   * @param correlationId String
   * @param logKey String
   * @return List<ProductDto>
   * @throws GeneralStatusCodeErrorException if an error occurs during the operation
   */
  List<ProductDto> getLowStockAlerts(String correlationId, String logKey)
      throws GeneralStatusCodeErrorException;
}
