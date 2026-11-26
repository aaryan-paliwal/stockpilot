package com.stockiq.inventory.repository.impl;

import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.common.util.SecurityUtils;
import com.stockiq.inventory.dto.WarehouseTransferDto;
import com.stockiq.inventory.entity.WarehouseTransfer;
import com.stockiq.inventory.repository.WarehouseTransferRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WarehouseTransferRepositoryImpl implements WarehouseTransferRepository {

  private static final String REPOSITORY = "warehouse_transfer_repository_impl";
  private static final String INIT_LOG = "init_log";

  private final JdbcTemplate jdbcTemplate;

  public WarehouseTransferRepositoryImpl(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private final RowMapper<WarehouseTransfer> entityRowMapper =
      (rs, rowNum) ->
          WarehouseTransfer.builder()
              .id(rs.getLong("id"))
              .userId(rs.getLong("user_id"))
              .sourceWarehouseId(rs.getLong("source_warehouse_id"))
              .destWarehouseId(rs.getLong("dest_warehouse_id"))
              .productId(rs.getLong("product_id"))
              .qty(rs.getInt("qty"))
              .status(rs.getString("status"))
              .referenceNote(rs.getString("reference_note"))
              .initiatingUser(rs.getString("initiating_user"))
              .approvingUser(rs.getString("approving_user"))
              .createdAt(rs.getTimestamp("created_at"))
              .approvedAt(rs.getTimestamp("approved_at"))
              .completedAt(rs.getTimestamp("completed_at"))
              .cancelledAt(rs.getTimestamp("cancelled_at"))
              .build();

  private final RowMapper<WarehouseTransferDto> dtoRowMapper =
      (rs, rowNum) ->
          WarehouseTransferDto.builder()
              .id(rs.getLong("id"))
              .sourceWarehouseId(rs.getLong("source_warehouse_id"))
              .sourceWarehouseCode(rs.getString("source_warehouse_code"))
              .sourceWarehouseName(rs.getString("source_warehouse_name"))
              .destWarehouseId(rs.getLong("dest_warehouse_id"))
              .destWarehouseCode(rs.getString("dest_warehouse_code"))
              .destWarehouseName(rs.getString("dest_warehouse_name"))
              .productId(rs.getLong("product_id"))
              .productSku(rs.getString("product_sku"))
              .productName(rs.getString("product_name"))
              .qty(rs.getInt("qty"))
              .status(rs.getString("status"))
              .referenceNote(rs.getString("reference_note"))
              .initiatingUser(rs.getString("initiating_user"))
              .approvingUser(rs.getString("approving_user"))
              .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null)
              .approvedAt(rs.getTimestamp("approved_at") != null ? rs.getTimestamp("approved_at").toString() : null)
              .completedAt(rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toString() : null)
              .cancelledAt(rs.getTimestamp("cancelled_at") != null ? rs.getTimestamp("cancelled_at").toString() : null)
              .build();

  private static final String BASE_SELECT_DTO =
      "SELECT wt.id, wt.source_warehouse_id, sw.code AS source_warehouse_code, sw.name AS source_warehouse_name, "
          + "wt.dest_warehouse_id, dw.code AS dest_warehouse_code, dw.name AS dest_warehouse_name, "
          + "wt.product_id, p.sku AS product_sku, p.name AS product_name, "
          + "wt.qty, wt.status, wt.reference_note, wt.initiating_user, wt.approving_user, "
          + "wt.created_at, wt.approved_at, wt.completed_at, wt.cancelled_at "
          + "FROM warehouse_transfers wt "
          + "JOIN warehouses sw ON wt.source_warehouse_id = sw.id "
          + "JOIN warehouses dw ON wt.dest_warehouse_id = dw.id "
          + "JOIN products p ON wt.product_id = p.id ";

  @Override
  public long save(String correlationId, String logKey, WarehouseTransfer transfer) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql =
        "INSERT INTO warehouse_transfers (user_id, source_warehouse_id, dest_warehouse_id, product_id, qty, status, reference_note, initiating_user, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    Long id = jdbcTemplate.queryForObject(
        sql,
        Long.class,
        userId,
        transfer.getSourceWarehouseId(),
        transfer.getDestWarehouseId(),
        transfer.getProductId(),
        transfer.getQty(),
        transfer.getStatus() != null ? transfer.getStatus() : "INITIATED",
        transfer.getReferenceNote(),
        transfer.getInitiatingUser(),
        now);

    return id != null ? id : 0L;
  }

  @Override
  public int updateStatus(String correlationId, String logKey, long transferId, String status, String approvingUser) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    Timestamp now = Timestamp.valueOf(LocalDateTime.now());

    if ("APPROVED".equalsIgnoreCase(status)) {
      String sql =
          "UPDATE warehouse_transfers SET status = ?, approving_user = ?, approved_at = ? WHERE id = ? AND user_id = ?";
      return jdbcTemplate.update(sql, status, approvingUser, now, transferId, userId);
    } else if ("COMPLETED".equalsIgnoreCase(status)) {
      String sql =
          "UPDATE warehouse_transfers SET status = ?, completed_at = ? WHERE id = ? AND user_id = ?";
      return jdbcTemplate.update(sql, status, now, transferId, userId);
    } else if ("CANCELLED".equalsIgnoreCase(status)) {
      String sql =
          "UPDATE warehouse_transfers SET status = ?, cancelled_at = ? WHERE id = ? AND user_id = ?";
      return jdbcTemplate.update(sql, status, now, transferId, userId);
    } else {
      String sql =
          "UPDATE warehouse_transfers SET status = ?, approving_user = ? WHERE id = ? AND user_id = ?";
      return jdbcTemplate.update(sql, status, approvingUser, transferId, userId);
    }
  }

  @Override
  public Optional<WarehouseTransfer> findById(String correlationId, String logKey, long id) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql = "SELECT * FROM warehouse_transfers WHERE id = ? AND user_id = ?";
    List<WarehouseTransfer> list = jdbcTemplate.query(sql, entityRowMapper, id, userId);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public Optional<WarehouseTransfer> findByIdForUpdate(String correlationId, String logKey, long id) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql = "SELECT * FROM warehouse_transfers WHERE id = ? AND user_id = ? FOR UPDATE";
    List<WarehouseTransfer> list = jdbcTemplate.query(sql, entityRowMapper, id, userId);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public List<WarehouseTransferDto> findAll(String correlationId, String logKey) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql = BASE_SELECT_DTO + "WHERE wt.user_id = ? ORDER BY wt.id DESC";
    return jdbcTemplate.query(sql, dtoRowMapper, userId);
  }

  @Override
  public List<WarehouseTransferDto> findByStatus(String correlationId, String logKey, String status) throws Exception {
    ActivityLog.logInfo(correlationId, logKey, REPOSITORY, INIT_LOG);
    Long userId = SecurityUtils.requireAuthenticatedUserId();

    String sql = BASE_SELECT_DTO + "WHERE wt.user_id = ? AND wt.status = ? ORDER BY wt.id DESC";
    return jdbcTemplate.query(sql, dtoRowMapper, userId, status);
  }
}
