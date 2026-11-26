package com.stockiq.inventory.service.impl;

import com.stockiq.inventory.common.util.SystemFormats;
import com.stockiq.inventory.common.enums.ErrorMessageCodeEnum;
import com.stockiq.inventory.common.enums.HttpStatusCodeEnum;
import com.stockiq.inventory.common.enums.audit.AuditAction;
import com.stockiq.inventory.common.enums.audit.AuditEntityType;
import com.stockiq.inventory.common.enums.stock.MovementType;
import com.stockiq.inventory.common.enums.stock.StockLevel;
import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.common.util.ErrorMessage;
import com.stockiq.inventory.common.util.SecurityUtils;
import com.stockiq.inventory.dto.ProductDto;
import com.stockiq.inventory.dto.StockDto;
import com.stockiq.inventory.entity.*;
import com.stockiq.inventory.repository.*;
import com.stockiq.inventory.service.AuditTrailService;
import com.stockiq.inventory.service.StockService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StockServiceImpl implements StockService {

  private static final String SERVICE_IMPL = "stock_service_impl";
  private static final String INIT_LOG = "init_log";
  private static final String EXCEPTION_LOG = "exception_log";

  private final ProductRepository productRepository;
  private final StockRepository stockRepository;
  private final NotificationRepository notificationRepository;
  private final AuditTrailService auditTrailService;
  private final WarehouseRepository warehouseRepository;
  private final WarehouseInventoryRepository warehouseInventoryRepository;

  public StockServiceImpl(
      ProductRepository productRepository,
      StockRepository stockRepository,
      NotificationRepository notificationRepository,
      AuditTrailService auditTrailService,
      WarehouseRepository warehouseRepository,
      WarehouseInventoryRepository warehouseInventoryRepository) {

    this.productRepository = productRepository;
    this.stockRepository = stockRepository;
    this.notificationRepository = notificationRepository;
    this.auditTrailService = auditTrailService;
    this.warehouseRepository = warehouseRepository;
    this.warehouseInventoryRepository = warehouseInventoryRepository;
  }

  @Override
  public StockDto logMovement(
      String correlationId, String logKey, StockDto movementDto, String loggedByUserId)
      throws GeneralStatusCodeErrorException {

    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);

      Long numericUserId = SecurityUtils.requireAuthenticatedUserId();
      String userId = String.valueOf(numericUserId);

      // 1. Resolve product with pessimistic lock (SELECT ... FOR UPDATE)
      Product product = null;

      if (movementDto.getProductId() != null && movementDto.getProductId() > 0) {
        product =
            productRepository
                .findByIdForUpdate(correlationId, logKey, movementDto.getProductId())
                .orElse(null);
      } else if (movementDto.getSku() != null && !movementDto.getSku().isBlank()) {
        Product bySku =
            productRepository
                .findBySku(correlationId, logKey, movementDto.getSku().trim())
                .orElse(null);
        if (bySku != null) {
          product =
              productRepository
                  .findByIdForUpdate(correlationId, logKey, bySku.getId())
                  .orElse(null);
        }
      } else if (movementDto.getProduct() != null && !movementDto.getProduct().isBlank()) {
        Product byName =
            productRepository
                .findByName(correlationId, logKey, movementDto.getProduct().trim())
                .orElse(null);
        if (byName != null) {
          product =
              productRepository
                  .findByIdForUpdate(correlationId, logKey, byName.getId())
                  .orElse(null);
        }
      }

      if (product == null) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Product not found",
                "Product Not Found",
                HttpStatus.NOT_FOUND,
                HttpStatusCodeEnum.NOT_FOUND.getValue(),
                ErrorMessageCodeEnum.STOCK_NOT_FOUND.getCode(),
                new java.util.Date().toString(),
                null));
      }

      // 2. Resolve warehouse (explicit or fallback to user's default warehouse)
      Warehouse warehouse = null;
      if (movementDto.getWarehouseId() != null && movementDto.getWarehouseId() > 0) {
        warehouse =
            warehouseRepository
                .findById(correlationId, logKey, movementDto.getWarehouseId())
                .orElseThrow(
                    () ->
                        new GeneralStatusCodeErrorException(
                            new ErrorMessage(
                                "Warehouse not found or not owned by you",
                                "Warehouse Not Found",
                                HttpStatus.NOT_FOUND,
                                HttpStatusCodeEnum.NOT_FOUND.getValue(),
                                ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                                new Date().toString(),
                                null)));
      } else if (movementDto.getWarehouseCode() != null && !movementDto.getWarehouseCode().isBlank()) {
        warehouse =
            warehouseRepository
                .findByCode(correlationId, logKey, movementDto.getWarehouseCode().trim().toUpperCase())
                .orElseThrow(
                    () ->
                        new GeneralStatusCodeErrorException(
                            new ErrorMessage(
                                "Warehouse not found with code: " + movementDto.getWarehouseCode(),
                                "Warehouse Not Found",
                                HttpStatus.NOT_FOUND,
                                HttpStatusCodeEnum.NOT_FOUND.getValue(),
                                ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                                new Date().toString(),
                                null)));
      } else {
        warehouse =
            warehouseRepository
                .findOrCreateDefaultWarehouse(correlationId, logKey, numericUserId)
                .orElseThrow(
                    () ->
                        new GeneralStatusCodeErrorException(
                            new ErrorMessage(
                                "Unable to resolve default warehouse",
                                "Warehouse Resolution Error",
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
                                ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
                                new Date().toString(),
                                null)));
      }

      if (!warehouse.isActive()) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Warehouse '" + warehouse.getCode() + "' is inactive",
                "Inactive Warehouse",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.VALIDATION_ERROR.getCode(),
                new Date().toString(),
                null));
      }

      // Ensure warehouse inventory row exists
      WarehouseInventory whInv =
          warehouseInventoryRepository
              .findByWarehouseAndProduct(correlationId, logKey, warehouse.getId(), product.getId())
              .orElse(null);

      if (whInv == null) {
        // If product already had stock from pre-warehouse migration, initialize with product.getQty(), else 0
        int initialWhQty = "WH-DEFAULT".equals(warehouse.getCode()) ? product.getQty() : 0;
        warehouseInventoryRepository.save(
            correlationId,
            logKey,
            WarehouseInventory.builder()
                .userId(numericUserId)
                .warehouseId(warehouse.getId())
                .productId(product.getId())
                .qty(initialWhQty)
                .build());
      }

      // Lock warehouse inventory row FOR UPDATE
      whInv =
          warehouseInventoryRepository
              .findByWarehouseAndProductForUpdate(correlationId, logKey, warehouse.getId(), product.getId())
              .orElseThrow();

      int previousQty = whInv.getQty();
      int newQty;
      MovementType type = movementDto.getType();
      int moveQty = movementDto.getQty() != null ? movementDto.getQty() : 0;

      if (type == MovementType.IN || type == MovementType.STOCK_IN) {
        type = MovementType.STOCK_IN;
        newQty = previousQty + moveQty;
      } else if (type == MovementType.OUT || type == MovementType.STOCK_OUT) {
        type = MovementType.STOCK_OUT;
        if (previousQty < moveQty) {
          throw new GeneralStatusCodeErrorException(
              new ErrorMessage(
                  "Insufficient stock in warehouse '" + warehouse.getCode() + "'. Available: " + previousQty + ", Requested: " + moveQty,
                  "Insufficient Stock",
                  HttpStatus.BAD_REQUEST,
                  HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                  ErrorMessageCodeEnum.STOCK_UPDATE_FAILED.getCode(),
                  new java.util.Date().toString(),
                  null));
        }
        newQty = previousQty - moveQty;
      } else if (type == MovementType.ADJUSTMENT) {
        if (moveQty < 0) {
          throw new GeneralStatusCodeErrorException(
              new ErrorMessage(
                  "Target adjusted quantity cannot be negative",
                  "Invalid Adjustment Quantity",
                  HttpStatus.BAD_REQUEST,
                  HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                  ErrorMessageCodeEnum.STOCK_UPDATE_FAILED.getCode(),
                  new java.util.Date().toString(),
                  null));
        }
        newQty = moveQty;
      } else {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Unsupported movement type: " + type,
                "Invalid Movement Type",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.STOCK_UPDATE_FAILED.getCode(),
                new java.util.Date().toString(),
                null));
      }

      // Update warehouse inventory balance
      warehouseInventoryRepository.updateQty(
          correlationId, logKey, warehouse.getId(), product.getId(), newQty);

      // Synchronize product aggregate stock
      int aggQty = warehouseInventoryRepository.getAggregateProductStock(
          correlationId, logKey, product.getId());
      product.setQty(aggQty);
      product.setUpdatedBy(userId);
      product.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
      productRepository.update(correlationId, logKey, product);

      // Insert immutable ledger entry
      Stock transaction =
          Stock.builder()
              .type(type.getValue())
              .typeName(type.name())
              .productId(product.getId())
              .warehouseId(warehouse.getId())
              .qty(moveQty)
              .previousQty(previousQty)
              .resultingQty(newQty)
              .reasonReference(movementDto.getReasonReference())
              .userId(Long.parseLong(userId))
              .createdBy(userId)
              .createdAt(Timestamp.valueOf(LocalDateTime.now()))
              .build();

      long transactionId = stockRepository.save(correlationId, logKey, transaction);

      // Audit trail
      AuditAction auditAction =
          (type == MovementType.STOCK_IN)
              ? AuditAction.STOCK_IN
              : (type == MovementType.STOCK_OUT)
                  ? AuditAction.STOCK_OUT
                  : AuditAction.UPDATE;

      auditTrailService.record(
          correlationId,
          logKey,
          auditAction,
          AuditEntityType.STOCK,
          transactionId,
          product.getName(),
          "WH=" + warehouse.getCode() + ", PrevQty=" + previousQty,
          "ResultingQty="
              + newQty
              + (movementDto.getReasonReference() != null ? " Reason=" + movementDto.getReasonReference() : ""));

      // Automatic notifications (restock or low stock based on total product level)
      try {
        if (type == MovementType.STOCK_IN) {
          Notification stockInNotif =
              Notification.builder()
                  .userId(numericUserId)
                  .title("Stock Restocked")
                  .message(
                      "Restocked "
                          + moveQty
                          + " units of '"
                          + product.getName()
                          + "' at "
                          + warehouse.getCode()
                          + ". Warehouse balance: "
                          + newQty)
                  .type("SUCCESS")
                  .isRead(false)
                  .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                  .build();
          notificationRepository.save(correlationId, logKey, stockInNotif);
        } else if (aggQty <= product.getReorderPoint()) {
          Notification lowStockAlert =
              Notification.builder()
                  .userId(numericUserId)
                  .title("Low Stock Warning")
                  .message(
                      "Product '"
                          + product.getName()
                          + "' has reached low aggregate stock level ("
                          + aggQty
                          + "/"
                          + product.getReorderPoint()
                          + "). Please reorder soon!")
                  .type("WARNING")
                  .isRead(false)
                  .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                  .build();
          notificationRepository.save(correlationId, logKey, lowStockAlert);
        }
      } catch (Exception e) {
        ActivityLog.logError(
            correlationId, logKey, SERVICE_IMPL, "NOTIFICATION_TRIGGER_ERROR", e.getMessage());
      }

      // Populate response DTO
      movementDto.setId(transactionId);
      movementDto.setWarehouseId(warehouse.getId());
      movementDto.setWarehouseCode(warehouse.getCode());
      movementDto.setWarehouseName(warehouse.getName());
      movementDto.setType(type);
      movementDto.setProductId(product.getId());
      movementDto.setProduct(product.getName());
      movementDto.setSku(product.getSku());
      movementDto.setPreviousQty(previousQty);
      movementDto.setResultingQty(newQty);
      movementDto.setDeltaQty(newQty - previousQty);
      movementDto.setUser(SecurityUtils.getCurrentUserEmail());
      movementDto.setDate(
          transaction
              .getCreatedAt()
              .toLocalDateTime()
              .format(DateTimeFormatter.ofPattern(SystemFormats.DATE_TIME_FORMAT_1)));

      return movementDto;

    } catch (GeneralStatusCodeErrorException e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw e;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to log stock movement: " + e.getMessage(),
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new java.util.Date().toString(),
              null));
    }
  }

  @Override
  public List<StockDto> getHistory(String correlationId, String logKey)
      throws GeneralStatusCodeErrorException {

    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      List<StockDto> history = stockRepository.findAll(correlationId, logKey);
      history.forEach(
          dto -> {
            if (dto.getDate() != null && dto.getDate().length() > 19) {
              dto.setDate(dto.getDate().substring(0, 19));
            }
          });
      return history;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to retrieve stock history",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new java.util.Date().toString(),
              null));
    }
  }

  @Override
  public List<StockDto> getHistoryByProduct(String correlationId, String logKey, long productId)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      List<StockDto> history = stockRepository.findByProductId(correlationId, logKey, productId);
      history.forEach(
          dto -> {
            if (dto.getDate() != null && dto.getDate().length() > 19) {
              dto.setDate(dto.getDate().substring(0, 19));
            }
          });
      return history;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to retrieve stock history for product",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new java.util.Date().toString(),
              null));
    }
  }

  @Override
  public List<ProductDto> getLowStockAlerts(String correlationId, String logKey)
      throws GeneralStatusCodeErrorException {

    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      List<ProductDto> products = productRepository.getLowStockAlerts(correlationId, logKey);
      products.forEach(
          dto -> dto.setStockLevel(StockLevel.fromQty(dto.getQty(), dto.getReorder())));
      return products;
    } catch (GeneralStatusCodeErrorException e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw e;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to get low stock products",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new java.util.Date().toString(),
              null));
    }
  }
}
