package com.stockiq.inventory.repository.impl;

import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.common.util.SecurityUtils;
import com.stockiq.inventory.entity.Warehouse;
import com.stockiq.inventory.repository.WarehouseRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class WarehouseRepositoryImpl implements WarehouseRepository {

  private static final String REPOSITORY = "warehouse_repository_impl";
  private static final String INIT_LOG = "init_log";

  private final JdbcTemplate jdbcTemplate;

  public WarehouseRepositoryImpl(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private final RowMapper<Warehouse> warehouseRowMapper =
      (rs, rowNum) ->
          Warehouse.builder()
              .id(rs.getLong("id"))
              .userId(rs.getLong("user_id"))
              .code(rs.getString("code"))
              .name(rs.getString("name"))
              .description(rs.getString("description"))
              .address(rs.getString("address"))
              .city(rs.getString("city"))
              .state(rs.getString("state"))
              .postalCode(rs.getString("postal_code"))
              .active(rs.getBoolean("is_active"))
              .createdAt(rs.getTimestamp("created_at"))
              .updatedAt(rs.getTimestamp("updated_at"))
              .build();

  @Override
  public long save(String correlationId, String logKey, Warehouse warehouse) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql =
        "INSERT INTO warehouses (user_id, code, name, description, address, city, state, postal_code, is_active, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    Long id = jdbcTemplate.queryForObject(
        sql,
        Long.class,
        userId,
        warehouse.getCode(),
        warehouse.getName(),
        warehouse.getDescription(),
        warehouse.getAddress(),
        warehouse.getCity(),
        warehouse.getState(),
        warehouse.getPostalCode(),
        warehouse.isActive(),
        now,
        now);

    return id != null ? id : 0L;
  }

  @Override
  public int update(String correlationId, String logKey, Warehouse warehouse) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql =
        "UPDATE warehouses SET name = ?, description = ?, address = ?, city = ?, state = ?, postal_code = ?, is_active = ?, updated_at = ? "
            + "WHERE id = ? AND user_id = ?";

    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    return jdbcTemplate.update(
        sql,
        warehouse.getName(),
        warehouse.getDescription(),
        warehouse.getAddress(),
        warehouse.getCity(),
        warehouse.getState(),
        warehouse.getPostalCode(),
        warehouse.isActive(),
        now,
        warehouse.getId(),
        userId);
  }

  @Override
  public Optional<Warehouse> findById(String correlationId, String logKey, long id) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql = "SELECT * FROM warehouses WHERE id = ? AND user_id = ?";
    List<Warehouse> list = jdbcTemplate.query(sql, warehouseRowMapper, id, userId);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public Optional<Warehouse> findByCode(String correlationId, String logKey, String code) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql = "SELECT * FROM warehouses WHERE code = ? AND user_id = ?";
    List<Warehouse> list = jdbcTemplate.query(sql, warehouseRowMapper, code, userId);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public List<Warehouse> findAll(String correlationId, String logKey) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql = "SELECT * FROM warehouses WHERE user_id = ? ORDER BY id ASC";
    return jdbcTemplate.query(sql, warehouseRowMapper, userId);
  }

  @Override
  public int deactivate(String correlationId, String logKey, long id) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql = "UPDATE warehouses SET is_active = FALSE, updated_at = ? WHERE id = ? AND user_id = ?";
    return jdbcTemplate.update(sql, Timestamp.valueOf(LocalDateTime.now()), id, userId);
  }

  @Override
  public Optional<Warehouse> findOrCreateDefaultWarehouse(String correlationId, String logKey, Long userId) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    String sql = "SELECT * FROM warehouses WHERE user_id = ? AND code = 'WH-DEFAULT'";
    List<Warehouse> list = jdbcTemplate.query(sql, warehouseRowMapper, userId);
    if (!list.isEmpty()) {
      return Optional.of(list.get(0));
    }

    // Create default warehouse
    String insertSql =
        "INSERT INTO warehouses (user_id, code, name, description, address, city, state, postal_code, is_active, created_at, updated_at) "
            + "VALUES (?, 'WH-DEFAULT', 'Primary Default Warehouse', 'Default migration warehouse', '100 Main St', 'Central', 'CA', '90001', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (user_id, code) DO UPDATE SET updated_at = CURRENT_TIMESTAMP RETURNING *";

    List<Warehouse> created = jdbcTemplate.query(insertSql, warehouseRowMapper, userId);
    return created.isEmpty() ? Optional.empty() : Optional.of(created.get(0));
  }
}

