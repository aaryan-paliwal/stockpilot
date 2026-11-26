package com.stockiq.inventory.repository;

import com.stockiq.inventory.dto.StockDto;
import com.stockiq.inventory.entity.Stock;
import java.util.List;

public interface StockRepository {

  /**
   * Save stock transaction.
   *
   * @param correlationId String
   * @param logKey String
   * @param transaction Stock
   * @return long
   * @throws Exception if an error occurs during the operation
   */
  long save(String correlationId, String logKey, Stock transaction) throws Exception;

  /**
   * Get all stock movement history.
   *
   * @param correlationId String
   * @param logKey String
   * @return List<StockDto>
   * @throws Exception if an error occurs during the operation
   */
  List<StockDto> findAll(String correlationId, String logKey) throws Exception;

  /**
   * Get stock movement history for a specific product.
   *
   * @param correlationId String
   * @param logKey String
   * @param productId long
   * @return List<StockDto>
   * @throws Exception if an error occurs during the operation
   */
  List<StockDto> findByProductId(String correlationId, String logKey, long productId) throws Exception;

  /**
   * Count all stock transactions.
   *
   * @param correlationId String
   * @param logKey String
   * @return long
   * @throws Exception if an error occurs during the operation
   */
  long countAll(String correlationId, String logKey) throws Exception;
}
