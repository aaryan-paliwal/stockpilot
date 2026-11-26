package com.stockiq.inventory.repository.impl;

import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.common.util.SecurityUtils;
import com.stockiq.inventory.dto.ProductBatchDto;
import com.stockiq.inventory.entity.ProductBatch;
import com.stockiq.inventory.repository.ProductBatchRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProductBatchRepositoryImpl implements ProductBatchRepository {

  private static final String REPOSITORY = "product_batch_repository_impl";
  private static final String INIT_LOG = "init_log";

  private final JdbcTemplate jdbcTemplate;

  public ProductBatchRepositoryImpl(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private final RowMapper<ProductBatch> entityRowMapper =
      (rs, rowNum) ->
          ProductBatch.builder()
              .id(rs.getLong("id"))
              .userId(rs.getLong("user_id"))
              .productId(rs.getLong("product_id"))
              .batchNumber(rs.getString("batch_number"))
              .mfgDate(rs.getDate("mfg_date"))
              .expiryDate(rs.getDate("expiry_date"))
              .costPrice(rs.getBigDecimal("cost_price"))
              .costEstimated(rs.getBoolean("is_cost_estimated"))
              .status(rs.getString("status"))
              .createdAt(rs.getTimestamp("created_at"))
              .updatedAt(rs.getTimestamp("updated_at"))
              .build();

  private final RowMapper<ProductBatchDto> dtoRowMapper =
      (rs, rowNum) ->
          ProductBatchDto.builder()
              .id(rs.getLong("id"))
              .userId(rs.getLong("user_id"))
              .productId(rs.getLong("product_id"))
              .productSku(rs.getString("product_sku"))
              .productName(rs.getString("product_name"))
              .batchNumber(rs.getString("batch_number"))
              .mfgDate(rs.getDate("mfg_date") != null ? rs.getDate("mfg_date").toString() : null)
              .expiryDate(rs.getDate("expiry_date") != null ? rs.getDate("expiry_date").toString() : null)
              .costPrice(rs.getBigDecimal("cost_price"))
              .costEstimated(rs.getBoolean("is_cost_estimated"))
              .status(rs.getString("status"))
              .totalQty(rs.getInt("total_qty"))
              .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null)
              .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toString() : null)
              .build();

  @Override
  public long save(String correlationId, String logKey, ProductBatch batch) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();
    Long userId = authUserId != null ? authUserId : batch.getUserId();

    String sql =
        """
        INSERT INTO product_batches 
        (user_id, product_id, batch_number, mfg_date, expiry_date, cost_price, is_cost_estimated, status, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING id
        """;

    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    return jdbcTemplate.queryForObject(
        sql,
        Long.class,
        userId,
        batch.getProductId(),
        batch.getBatchNumber(),
        batch.getMfgDate(),
        batch.getExpiryDate(),
        batch.getCostPrice() != null ? batch.getCostPrice() : java.math.BigDecimal.ZERO,
        batch.isCostEstimated(),
        batch.getStatus() != null ? batch.getStatus() : "ACTIVE",
        batch.getCreatedAt() != null ? batch.getCreatedAt() : now,
        batch.getUpdatedAt() != null ? batch.getUpdatedAt() : now);
  }

  @Override
  public int update(String correlationId, String logKey, ProductBatch batch) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();
    Long userId = authUserId != null ? authUserId : batch.getUserId();

    String sql =
        """
        UPDATE product_batches
        SET batch_number = ?,
            mfg_date = ?,
            expiry_date = ?,
            cost_price = ?,
            is_cost_estimated = ?,
            status = ?,
            updated_at = ?
        WHERE id = ? AND user_id = ?
        """;

    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    return jdbcTemplate.update(
        sql,
        batch.getBatchNumber(),
        batch.getMfgDate(),
        batch.getExpiryDate(),
        batch.getCostPrice(),
        batch.isCostEstimated(),
        batch.getStatus(),
        now,
        batch.getId(),
        userId);
  }

  @Override
  public Optional<ProductBatch> findById(String correlationId, String logKey, long id) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();

    String sql = "SELECT * FROM product_batches WHERE id = ?" + (authUserId != null ? " AND user_id = ?" : "");
    List<ProductBatch> list =
        authUserId != null
            ? jdbcTemplate.query(sql, entityRowMapper, id, authUserId)
            : jdbcTemplate.query(sql, entityRowMapper, id);

    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public Optional<ProductBatch> findByProductAndBatchNumber(
      String correlationId, String logKey, long productId, String batchNumber) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();

    String sql =
        "SELECT * FROM product_batches WHERE product_id = ? AND batch_number = ?"
            + (authUserId != null ? " AND user_id = ?" : "");

    List<ProductBatch> list =
        authUserId != null
            ? jdbcTemplate.query(sql, entityRowMapper, productId, batchNumber, authUserId)
            : jdbcTemplate.query(sql, entityRowMapper, productId, batchNumber);

    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public List<ProductBatchDto> findByProductId(String correlationId, String logKey, long productId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();

    String sql =
        """
        SELECT 
            b.id,
            b.user_id,
            b.product_id,
            p.sku AS product_sku,
            p.name AS product_name,
            b.batch_number,
            b.mfg_date,
            b.expiry_date,
            b.cost_price,
            b.is_cost_estimated,
            b.status,
            COALESCE(SUM(bi.qty), 0) AS total_qty,
            b.created_at,
            b.updated_at
        FROM product_batches b
        JOIN products p ON p.id = b.product_id
        LEFT JOIN batch_inventory bi ON bi.batch_id = b.id
        WHERE b.product_id = ?
        """
            + (authUserId != null ? " AND b.user_id = ? " : " ")
            + """
        GROUP BY b.id, b.user_id, b.product_id, p.sku, p.name, b.batch_number, b.mfg_date, b.expiry_date, b.cost_price, b.is_cost_estimated, b.status, b.created_at, b.updated_at
        ORDER BY b.expiry_date ASC NULLS LAST, b.created_at ASC
        """;

    return authUserId != null
        ? jdbcTemplate.query(sql, dtoRowMapper, productId, authUserId)
        : jdbcTemplate.query(sql, dtoRowMapper, productId);
  }

  @Override
  public List<ProductBatchDto> findExpiringBatches(
      String correlationId, String logKey, int daysThreshold) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long authUserId = SecurityUtils.getCurrentUserIdAsLong();

    String sql =
        """
        SELECT 
            b.id,
            b.user_id,
            b.product_id,
            p.sku AS product_sku,
            p.name AS product_name,
            b.batch_number,
            b.mfg_date,
            b.expiry_date,
            b.cost_price,
            b.is_cost_estimated,
            b.status,
            COALESCE(SUM(bi.qty), 0) AS total_qty,
            b.created_at,
            b.updated_at
        FROM product_batches b
        JOIN products p ON p.id = b.product_id
        LEFT JOIN batch_inventory bi ON bi.batch_id = b.id
        WHERE b.expiry_date IS NOT NULL
          AND b.expiry_date <= CURRENT_DATE + (? * INTERVAL '1 day')
          AND b.status = 'ACTIVE'
        """
            + (authUserId != null ? " AND b.user_id = ? " : " ")
            + """
        GROUP BY b.id, b.user_id, b.product_id, p.sku, p.name, b.batch_number, b.mfg_date, b.expiry_date, b.cost_price, b.is_cost_estimated, b.status, b.created_at, b.updated_at
        HAVING COALESCE(SUM(bi.qty), 0) > 0
        ORDER BY b.expiry_date ASC
        """;

    return authUserId != null
        ? jdbcTemplate.query(sql, dtoRowMapper, daysThreshold, authUserId)
        : jdbcTemplate.query(sql, dtoRowMapper, daysThreshold);
  }
}

