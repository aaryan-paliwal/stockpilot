package com.stockiq.inventory.repository.impl;

import com.stockiq.inventory.common.enums.stock.MovementType;
import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.dto.StockDto;
import com.stockiq.inventory.entity.Stock;
import com.stockiq.inventory.repository.StockRepository;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class StockRepositoryImpl implements StockRepository {

  private static final String REPO_IMPL = "stock_repo_impl";
  private static final String INIT_LOG = "init_log";

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public StockRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Save stock transaction.
   *
   * @param correlationId String
   * @param logKey String
   * @param transaction Stock
   * @return long
   */
  @Override
  public long save(String correlationId, String logKey, Stock transaction) {

    ActivityLog.logInfo(correlationId, logKey, REPO_IMPL, INIT_LOG);

    String typeStr;
    if (transaction.getTypeName() != null && !transaction.getTypeName().isBlank()) {
      MovementType normalized = MovementType.fromString(transaction.getTypeName());
      typeStr = normalized.name(); // Always STOCK_IN, STOCK_OUT, or ADJUSTMENT
    } else {
      typeStr = transaction.getType() == 2 ? "ADJUSTMENT" : (transaction.getType() == 1 ? "STOCK_IN" : "STOCK_OUT");
    }

    String sql =
        """
        INSERT INTO stock_transactions
        (
            user_id,
            warehouse_id,
            type,
            product_id,
            qty,
            previous_qty,
            resulting_qty,
            reason_reference,
            created_by,
            created_at
        )
        VALUES
        (
            :userId,
            :warehouseId,
            :type,
            :productId,
            :qty,
            :previousQty,
            :resultingQty,
            :reasonReference,
            :createdBy,
            :createdAt
        )
        """;

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", transaction.getUserId())
            .addValue("warehouseId", transaction.getWarehouseId())
            .addValue("type", typeStr)
            .addValue("productId", transaction.getProductId())
            .addValue("qty", transaction.getQty())
            .addValue("previousQty", transaction.getPreviousQty())
            .addValue("resultingQty", transaction.getResultingQty())
            .addValue("reasonReference", transaction.getReasonReference())
            .addValue("createdBy", transaction.getCreatedBy())
            .addValue("createdAt", transaction.getCreatedAt());

    KeyHolder keyHolder = new GeneratedKeyHolder();

    jdbcTemplate.update(sql, params, keyHolder, new String[] {"id"});

    return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : 0L;
  }

  /**
   * Get all stock movement history.
   *
   * @param correlationId String
   * @param logKey String
   * @return List<StockDto>
   */
  @Override
  public List<StockDto> findAll(String correlationId, String logKey) {

    ActivityLog.logInfo(correlationId, logKey, REPO_IMPL, INIT_LOG);

    String sql =
        """
        SELECT
            t.id,
            t.warehouse_id AS warehouseId,
            w.code AS warehouseCode,
            w.name AS warehouseName,
            t.type,
            t.product_id AS productId,
            p.sku,
            p.name AS product,
            t.qty,
            t.previous_qty AS previousQty,
            t.resulting_qty AS resultingQty,
            t.reason_reference AS reasonReference,
            t.created_at AS date,
            COALESCE(u.email, t.created_by) AS user
        FROM stock_transactions t
        LEFT JOIN warehouses w
            ON w.id = t.warehouse_id
        LEFT JOIN products p
            ON p.id = t.product_id
        LEFT JOIN users u
            ON t.user_id = u.id
        WHERE t.user_id = :userId
        ORDER BY t.created_at DESC
        """;

    return jdbcTemplate.query(
        sql,
        new MapSqlParameterSource(
            "userId", com.stockiq.inventory.common.util.SecurityUtils.getCurrentUserIdAsLong()),
        (rs, rowNum) -> {
          int prev = rs.getInt("previousQty");
          int resulting = rs.getInt("resultingQty");
          Long whId = rs.getObject("warehouseId") != null ? rs.getLong("warehouseId") : null;
          return StockDto.builder()
              .id(rs.getLong("id"))
              .warehouseId(whId)
              .warehouseCode(rs.getString("warehouseCode"))
              .warehouseName(rs.getString("warehouseName"))
              .productId(rs.getLong("productId"))
              .sku(rs.getString("sku"))
              .type(parseMovementType(rs.getString("type")))
              .product(rs.getString("product"))
              .qty(rs.getInt("qty"))
              .previousQty(prev)
              .resultingQty(resulting)
              .deltaQty(resulting - prev)
              .reasonReference(rs.getString("reasonReference"))
              .date(rs.getTimestamp("date") != null ? rs.getTimestamp("date").toString() : null)
              .user(rs.getString("user"))
              .build();
        });
  }

  /**
   * Get stock movement history for a specific product.
   *
   * @param correlationId String
   * @param logKey String
   * @param productId long
   * @return List<StockDto>
   */
  @Override
  public List<StockDto> findByProductId(String correlationId, String logKey, long productId) {

    ActivityLog.logInfo(correlationId, logKey, REPO_IMPL, INIT_LOG);

    String sql =
        """
        SELECT
            t.id,
            t.warehouse_id AS warehouseId,
            w.code AS warehouseCode,
            w.name AS warehouseName,
            t.type,
            t.product_id AS productId,
            p.sku,
            p.name AS product,
            t.qty,
            t.previous_qty AS previousQty,
            t.resulting_qty AS resultingQty,
            t.reason_reference AS reasonReference,
            t.created_at AS date,
            COALESCE(u.email, t.created_by) AS user
        FROM stock_transactions t
        LEFT JOIN warehouses w
            ON w.id = t.warehouse_id
        LEFT JOIN products p
            ON p.id = t.product_id
        LEFT JOIN users u
            ON t.user_id = u.id
        WHERE t.user_id = :userId AND t.product_id = :productId
        ORDER BY t.created_at DESC
        """;

    return jdbcTemplate.query(
        sql,
        new MapSqlParameterSource()
            .addValue("userId", com.stockiq.inventory.common.util.SecurityUtils.getCurrentUserIdAsLong())
            .addValue("productId", productId),
        (rs, rowNum) -> {
          int prev = rs.getInt("previousQty");
          int resulting = rs.getInt("resultingQty");
          Long whId = rs.getObject("warehouseId") != null ? rs.getLong("warehouseId") : null;
          return StockDto.builder()
              .id(rs.getLong("id"))
              .warehouseId(whId)
              .warehouseCode(rs.getString("warehouseCode"))
              .warehouseName(rs.getString("warehouseName"))
              .productId(rs.getLong("productId"))
              .sku(rs.getString("sku"))
              .type(parseMovementType(rs.getString("type")))
              .product(rs.getString("product"))
              .qty(rs.getInt("qty"))
              .previousQty(prev)
              .resultingQty(resulting)
              .deltaQty(resulting - prev)
              .reasonReference(rs.getString("reasonReference"))
              .date(rs.getTimestamp("date") != null ? rs.getTimestamp("date").toString() : null)
              .user(rs.getString("user"))
              .build();
        });
  }

  /**
   * Count all stock transactions.
   *
   * @param correlationId String
   * @param logKey String
   * @return long
   */
  @Override
  public long countAll(String correlationId, String logKey) {

    ActivityLog.logInfo(correlationId, logKey, REPO_IMPL, INIT_LOG);

    String sql =
        """
        SELECT COUNT(*)
        FROM stock_transactions
        WHERE user_id = :userId
        """;

    Long count =
        jdbcTemplate.queryForObject(
            sql,
            new MapSqlParameterSource(
                "userId", com.stockiq.inventory.common.util.SecurityUtils.getCurrentUserIdAsLong()),
            Long.class);

    return count != null ? count : 0L;
  }

  private MovementType parseMovementType(String typeStr) {
    if (typeStr == null) return MovementType.STOCK_IN;
    try {
      int intVal = Integer.parseInt(typeStr);
      return MovementType.fromValue(intVal);
    } catch (NumberFormatException e) {
      return MovementType.fromString(typeStr);
    }
  }
}
