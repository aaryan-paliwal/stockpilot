package com.stockiq.inventory.repository.impl;

import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.common.util.SecurityUtils;
import com.stockiq.inventory.dto.BatchInventoryDto;
import com.stockiq.inventory.entity.BatchInventory;
import com.stockiq.inventory.repository.BatchInventoryRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BatchInventoryRepositoryImpl implements BatchInventoryRepository {

  private static final String REPOSITORY = "batch_inventory_repository_impl";
  private static final String INIT_LOG = "init_log";

  private final JdbcTemplate jdbcTemplate;

  public BatchInventoryRepositoryImpl(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private final RowMapper<BatchInventory> entityRowMapper =
      (rs, rowNum) ->
          BatchInventory.builder()
              .id(rs.getLong("id"))
              .userId(rs.getLong("user_id"))
              .batchId(rs.getLong("batch_id"))
              .warehouseId(rs.getLong("warehouse_id"))
              .productId(rs.getLong("product_id"))
              .qty(rs.getInt("qty"))
              .createdAt(rs.getTimestamp("created_at"))
              .updatedAt(rs.getTimestamp("updated_at"))
              .build();

  private final RowMapper<BatchInventoryDto> dtoRowMapper =
      (rs, rowNum) ->
          BatchInventoryDto.builder()
              .id(rs.getLong("id"))
              .batchId(rs.getLong("batch_id"))
              .batchNumber(rs.getString("batch_number"))
              .warehouseId(rs.getLong("warehouse_id"))
              .warehouseCode(rs.getString("warehouse_code"))
              .warehouseName(rs.getString("warehouse_name"))
              .productId(rs.getLong("product_id"))
              .productSku(rs.getString("product_sku"))
              .productName(rs.getString("product_name"))
              .qty(rs.getInt("qty"))
              .expiryDate(rs.getDate("expiry_date") != null ? rs.getDate("expiry_date").toString() : null)
              .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toString() : null)
              .build();

  @Override
  public long save(String correlationId, String logKey, BatchInventory inv) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();
    Long userId = authUserId != null ? authUserId : inv.getUserId();

    String sql =
        """
        INSERT INTO batch_inventory (user_id, batch_id, warehouse_id, product_id, qty, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        RETURNING id
        """;

    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    return jdbcTemplate.queryForObject(
        sql,
        Long.class,
        userId,
        inv.getBatchId(),
        inv.getWarehouseId(),
        inv.getProductId(),
        inv.getQty(),
        inv.getCreatedAt() != null ? inv.getCreatedAt() : now,
        inv.getUpdatedAt() != null ? inv.getUpdatedAt() : now);
  }

  @Override
  public int updateQty(String correlationId, String logKey, long batchId, long warehouseId, int newQty) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();

    String sql =
        "UPDATE batch_inventory SET qty = ?, updated_at = ? WHERE batch_id = ? AND warehouse_id = ?"
            + (authUserId != null ? " AND user_id = ?" : "");

    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    return authUserId != null
        ? jdbcTemplate.update(sql, newQty, now, batchId, warehouseId, authUserId)
        : jdbcTemplate.update(sql, newQty, now, batchId, warehouseId);
  }

  @Override
  public Optional<BatchInventory> findByBatchAndWarehouse(
      String correlationId, String logKey, long batchId, long warehouseId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();

    String sql =
        "SELECT * FROM batch_inventory WHERE batch_id = ? AND warehouse_id = ?"
            + (authUserId != null ? " AND user_id = ?" : "");

    List<BatchInventory> list =
        authUserId != null
            ? jdbcTemplate.query(sql, entityRowMapper, batchId, warehouseId, authUserId)
            : jdbcTemplate.query(sql, entityRowMapper, batchId, warehouseId);

    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public Optional<BatchInventory> findByBatchAndWarehouseForUpdate(
      String correlationId, String logKey, long batchId, long warehouseId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();

    String sql =
        "SELECT * FROM batch_inventory WHERE batch_id = ? AND warehouse_id = ?"
            + (authUserId != null ? " AND user_id = ?" : "")
            + " FOR UPDATE";

    List<BatchInventory> list =
        authUserId != null
            ? jdbcTemplate.query(sql, entityRowMapper, batchId, warehouseId, authUserId)
            : jdbcTemplate.query(sql, entityRowMapper, batchId, warehouseId);

    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public List<BatchInventoryDto> findByWarehouseAndProduct(
      String correlationId, String logKey, long warehouseId, long productId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();

    String sql =
        """
        SELECT 
            bi.id,
            bi.batch_id,
            b.batch_number,
            bi.warehouse_id,
            w.code AS warehouse_code,
            w.name AS warehouse_name,
            bi.product_id,
            p.sku AS product_sku,
            p.name AS product_name,
            bi.qty,
            b.expiry_date,
            bi.updated_at
        FROM batch_inventory bi
        JOIN product_batches b ON b.id = bi.batch_id
        JOIN warehouses w ON w.id = bi.warehouse_id
        JOIN products p ON p.id = bi.product_id
        WHERE bi.warehouse_id = ? AND bi.product_id = ?
        """
            + (authUserId != null ? " AND bi.user_id = ? " : " ")
            + "ORDER BY b.expiry_date ASC NULLS LAST, b.created_at ASC";

    return authUserId != null
        ? jdbcTemplate.query(sql, dtoRowMapper, warehouseId, productId, authUserId)
        : jdbcTemplate.query(sql, dtoRowMapper, warehouseId, productId);
  }

  @Override
  public List<BatchInventoryDto> findFefoCandidates(
      String correlationId, String logKey, long warehouseId, long productId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();

    String sql =
        """
        SELECT 
            bi.id,
            bi.batch_id,
            b.batch_number,
            bi.warehouse_id,
            w.code AS warehouse_code,
            w.name AS warehouse_name,
            bi.product_id,
            p.sku AS product_sku,
            p.name AS product_name,
            bi.qty,
            b.expiry_date,
            bi.updated_at
        FROM batch_inventory bi
        JOIN product_batches b ON b.id = bi.batch_id
        JOIN warehouses w ON w.id = bi.warehouse_id
        JOIN products p ON p.id = bi.product_id
        WHERE bi.warehouse_id = ? 
          AND bi.product_id = ? 
          AND bi.qty > 0 
          AND b.status = 'ACTIVE'
        """
            + (authUserId != null ? " AND bi.user_id = ? " : " ")
            + "ORDER BY b.expiry_date ASC NULLS LAST, b.created_at ASC";

    return authUserId != null
        ? jdbcTemplate.query(sql, dtoRowMapper, warehouseId, productId, authUserId)
        : jdbcTemplate.query(sql, dtoRowMapper, warehouseId, productId);
  }

  @Override
  public int getAggregateBatchStock(String correlationId, String logKey, long batchId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();

    String sql =
        "SELECT COALESCE(SUM(qty), 0) FROM batch_inventory WHERE batch_id = ?"
            + (authUserId != null ? " AND user_id = ?" : "");

    Integer sum =
        authUserId != null
            ? jdbcTemplate.queryForObject(sql, Integer.class, batchId, authUserId)
            : jdbcTemplate.queryForObject(sql, Integer.class, batchId);

    return sum != null ? sum : 0;
  }
}

