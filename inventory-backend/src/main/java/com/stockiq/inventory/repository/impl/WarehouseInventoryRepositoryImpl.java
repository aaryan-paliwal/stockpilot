package com.stockiq.inventory.repository.impl;

import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.common.util.SecurityUtils;
import com.stockiq.inventory.dto.WarehouseInventoryDto;
import com.stockiq.inventory.entity.WarehouseInventory;
import com.stockiq.inventory.repository.WarehouseInventoryRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WarehouseInventoryRepositoryImpl implements WarehouseInventoryRepository {

  private static final String REPOSITORY = "warehouse_inventory_repository_impl";
  private static final String INIT_LOG = "init_log";

  private final JdbcTemplate jdbcTemplate;

  public WarehouseInventoryRepositoryImpl(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private final RowMapper<WarehouseInventory> entityRowMapper =
      (rs, rowNum) ->
          WarehouseInventory.builder()
              .id(rs.getLong("id"))
              .userId(rs.getLong("user_id"))
              .warehouseId(rs.getLong("warehouse_id"))
              .productId(rs.getLong("product_id"))
              .qty(rs.getInt("qty"))
              .createdAt(rs.getTimestamp("created_at"))
              .updatedAt(rs.getTimestamp("updated_at"))
              .build();

  private final RowMapper<WarehouseInventoryDto> dtoRowMapper =
      (rs, rowNum) ->
          WarehouseInventoryDto.builder()
              .id(rs.getLong("id"))
              .warehouseId(rs.getLong("warehouse_id"))
              .warehouseCode(rs.getString("warehouse_code"))
              .warehouseName(rs.getString("warehouse_name"))
              .productId(rs.getLong("product_id"))
              .productSku(rs.getString("product_sku"))
              .productName(rs.getString("product_name"))
              .qty(rs.getInt("qty"))
              .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toString() : null)
              .build();

  @Override
  public long save(String correlationId, String logKey, WarehouseInventory inv) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql =
        "INSERT INTO warehouse_inventory (user_id, warehouse_id, product_id, qty, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (warehouse_id, product_id) DO NOTHING RETURNING id";

    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    List<Long> ids =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) -> rs.getLong(1),
            userId,
            inv.getWarehouseId(),
            inv.getProductId(),
            inv.getQty(),
            now,
            now);

    return ids.isEmpty() ? 0L : ids.get(0);
  }

  @Override
  public int updateQty(String correlationId, String logKey, long warehouseId, long productId, int newQty) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql =
        "UPDATE warehouse_inventory SET qty = ?, updated_at = ? WHERE warehouse_id = ? AND product_id = ? AND user_id = ?";
    return jdbcTemplate.update(sql, newQty, Timestamp.valueOf(LocalDateTime.now()), warehouseId, productId, userId);
  }

  @Override
  public Optional<WarehouseInventory> findByWarehouseAndProduct(
      String correlationId, String logKey, long warehouseId, long productId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql = "SELECT * FROM warehouse_inventory WHERE warehouse_id = ? AND product_id = ? AND user_id = ?";
    List<WarehouseInventory> list = jdbcTemplate.query(sql, entityRowMapper, warehouseId, productId, userId);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public Optional<WarehouseInventory> findByWarehouseAndProductForUpdate(
      String correlationId, String logKey, long warehouseId, long productId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    // Pessimistic lock row
    String sql = "SELECT * FROM warehouse_inventory WHERE warehouse_id = ? AND product_id = ? AND user_id = ? FOR UPDATE";
    List<WarehouseInventory> list = jdbcTemplate.query(sql, entityRowMapper, warehouseId, productId, userId);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public List<WarehouseInventoryDto> findByWarehouseId(String correlationId, String logKey, long warehouseId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql =
        "SELECT wi.id, wi.warehouse_id, w.code AS warehouse_code, w.name AS warehouse_name, "
            + "wi.product_id, p.sku AS product_sku, p.name AS product_name, wi.qty, wi.updated_at "
            + "FROM warehouse_inventory wi "
            + "JOIN warehouses w ON wi.warehouse_id = w.id "
            + "JOIN products p ON wi.product_id = p.id "
            + "WHERE wi.warehouse_id = ? AND wi.user_id = ? ORDER BY p.name ASC";

    return jdbcTemplate.query(sql, dtoRowMapper, warehouseId, userId);
  }

  @Override
  public List<WarehouseInventoryDto> findByProductId(String correlationId, String logKey, long productId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql =
        "SELECT wi.id, wi.warehouse_id, w.code AS warehouse_code, w.name AS warehouse_name, "
            + "wi.product_id, p.sku AS product_sku, p.name AS product_name, wi.qty, wi.updated_at "
            + "FROM warehouse_inventory wi "
            + "JOIN warehouses w ON wi.warehouse_id = w.id "
            + "JOIN products p ON wi.product_id = p.id "
            + "WHERE wi.product_id = ? AND wi.user_id = ? ORDER BY w.code ASC";

    return jdbcTemplate.query(sql, dtoRowMapper, productId, userId);
  }

  @Override
  public int getAggregateProductStock(String correlationId, String logKey, long productId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql = "SELECT COALESCE(SUM(qty), 0) FROM warehouse_inventory WHERE product_id = ? AND user_id = ?";
    Integer total = jdbcTemplate.queryForObject(sql, Integer.class, productId, userId);
    return total != null ? total : 0;
  }
}
