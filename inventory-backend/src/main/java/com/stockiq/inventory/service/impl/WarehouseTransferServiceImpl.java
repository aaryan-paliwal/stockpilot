package com.stockiq.inventory.service.impl;

import com.stockiq.inventory.common.enums.ErrorMessageCodeEnum;
import com.stockiq.inventory.common.enums.HttpStatusCodeEnum;
import com.stockiq.inventory.common.enums.audit.AuditAction;
import com.stockiq.inventory.common.enums.audit.AuditEntityType;
import com.stockiq.inventory.common.enums.stock.MovementType;
import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.common.util.ErrorMessage;
import com.stockiq.inventory.common.util.SecurityUtils;
import com.stockiq.inventory.dto.WarehouseTransferDto;
import com.stockiq.inventory.entity.*;
import com.stockiq.inventory.repository.*;
import com.stockiq.inventory.service.AuditTrailService;
import com.stockiq.inventory.service.WarehouseTransferService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WarehouseTransferServiceImpl implements WarehouseTransferService {

  private static final String SERVICE_IMPL = "warehouse_transfer_service_impl";
  private static final String INIT_LOG = "init_log";
  private static final String EXCEPTION_LOG = "exception_log";

  private final WarehouseTransferRepository warehouseTransferRepository;
  private final WarehouseRepository warehouseRepository;
  private final ProductRepository productRepository;
  private final WarehouseInventoryRepository warehouseInventoryRepository;
  private final StockRepository stockRepository;
  private final AuditTrailService auditTrailService;

  public WarehouseTransferServiceImpl(
      WarehouseTransferRepository warehouseTransferRepository,
      WarehouseRepository warehouseRepository,
      ProductRepository productRepository,
      WarehouseInventoryRepository warehouseInventoryRepository,
      StockRepository stockRepository,
      AuditTrailService auditTrailService) {
    this.warehouseTransferRepository = warehouseTransferRepository;
    this.warehouseRepository = warehouseRepository;
    this.productRepository = productRepository;
    this.warehouseInventoryRepository = warehouseInventoryRepository;
    this.stockRepository = stockRepository;
    this.auditTrailService = auditTrailService;
  }

  @Override
  public WarehouseTransferDto initiateTransfer(
      String correlationId, String logKey, WarehouseTransferDto dto)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      Long userId = SecurityUtils.requireAuthenticatedUserId();

      if (dto.getSourceWarehouseId().equals(dto.getDestWarehouseId())) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Source and destination warehouses must be different",
                "Invalid Transfer Warehouses",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.VALIDATION_ERROR.getCode(),
                new Date().toString(),
                null));
      }

      if (dto.getQty() == null || dto.getQty() <= 0) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Transfer quantity must be strictly positive",
                "Invalid Transfer Quantity",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.VALIDATION_ERROR.getCode(),
                new Date().toString(),
                null));
      }

      // Verify source warehouse ownership
      Warehouse srcWh =
          warehouseRepository
              .findById(correlationId, logKey, dto.getSourceWarehouseId())
              .orElseThrow(
                  () ->
                      new GeneralStatusCodeErrorException(
                          new ErrorMessage(
                              "Source warehouse not found or not owned by you",
                              "Source Warehouse Not Found",
                              HttpStatus.NOT_FOUND,
                              HttpStatusCodeEnum.NOT_FOUND.getValue(),
                              ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                              new Date().toString(),
                              null)));

      if (!srcWh.isActive()) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Source warehouse is inactive",
                "Inactive Warehouse",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.VALIDATION_ERROR.getCode(),
                new Date().toString(),
                null));
      }

      // Verify dest warehouse ownership
      Warehouse destWh =
          warehouseRepository
              .findById(correlationId, logKey, dto.getDestWarehouseId())
              .orElseThrow(
                  () ->
                      new GeneralStatusCodeErrorException(
                          new ErrorMessage(
                              "Destination warehouse not found or not owned by you",
                              "Destination Warehouse Not Found",
                              HttpStatus.NOT_FOUND,
                              HttpStatusCodeEnum.NOT_FOUND.getValue(),
                              ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                              new Date().toString(),
                              null)));

      if (!destWh.isActive()) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Destination warehouse is inactive",
                "Inactive Warehouse",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.VALIDATION_ERROR.getCode(),
                new Date().toString(),
                null));
      }

      // Verify product ownership
      productRepository
          .findById(correlationId, logKey, dto.getProductId())
          .orElseThrow(
              () ->
                  new GeneralStatusCodeErrorException(
                      new ErrorMessage(
                          "Product not found or not owned by you",
                          "Product Not Found",
                          HttpStatus.NOT_FOUND,
                          HttpStatusCodeEnum.NOT_FOUND.getValue(),
                          ErrorMessageCodeEnum.PRODUCT_NOT_FOUND.getCode(),
                          new Date().toString(),
                          null)));

      // Check current source balance
      WarehouseInventory srcInv =
          warehouseInventoryRepository
              .findByWarehouseAndProduct(
                  correlationId, logKey, dto.getSourceWarehouseId(), dto.getProductId())
              .orElse(null);

      int currentSrcQty = srcInv != null ? srcInv.getQty() : 0;
      if (currentSrcQty < dto.getQty()) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Insufficient stock in source warehouse. Available: "
                    + currentSrcQty
                    + ", Requested: "
                    + dto.getQty(),
                "Insufficient Stock",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.STOCK_UPDATE_FAILED.getCode(),
                new Date().toString(),
                null));
      }

      String userIdentifier = String.valueOf(userId);

      WarehouseTransfer transfer =
          WarehouseTransfer.builder()
              .userId(userId)
              .sourceWarehouseId(dto.getSourceWarehouseId())
              .destWarehouseId(dto.getDestWarehouseId())
              .productId(dto.getProductId())
              .qty(dto.getQty())
              .status("INITIATED")
              .referenceNote(dto.getReferenceNote())
              .initiatingUser(userIdentifier)
              .build();

      long transferId = warehouseTransferRepository.save(correlationId, logKey, transfer);
      dto.setId(transferId);
      dto.setStatus("INITIATED");
      dto.setInitiatingUser(userIdentifier);
      dto.setSourceWarehouseCode(srcWh.getCode());
      dto.setSourceWarehouseName(srcWh.getName());
      dto.setDestWarehouseCode(destWh.getCode());
      dto.setDestWarehouseName(destWh.getName());

      return dto;
    } catch (GeneralStatusCodeErrorException e) {
      throw e;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to initiate warehouse transfer: " + e.getMessage(),
              "Transfer Initiation Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  @Override
  public WarehouseTransferDto approveTransfer(String correlationId, String logKey, long transferId)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      Long userId = SecurityUtils.requireAuthenticatedUserId();
      String userIdentifier = String.valueOf(userId);

      WarehouseTransfer transfer =
          warehouseTransferRepository
              .findByIdForUpdate(correlationId, logKey, transferId)
              .orElseThrow(
                  () ->
                      new GeneralStatusCodeErrorException(
                          new ErrorMessage(
                              "Warehouse transfer not found",
                              "Transfer Not Found",
                              HttpStatus.NOT_FOUND,
                              HttpStatusCodeEnum.NOT_FOUND.getValue(),
                              ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                              new Date().toString(),
                              null)));

      if ("APPROVED".equalsIgnoreCase(transfer.getStatus())) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Transfer is already approved",
                "Repeated Approval Error",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.VALIDATION_ERROR.getCode(),
                new Date().toString(),
                null));
      }

      if (!"INITIATED".equalsIgnoreCase(transfer.getStatus())) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Only INITIATED transfers can be approved. Current status: " + transfer.getStatus(),
                "Invalid Transfer State",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.VALIDATION_ERROR.getCode(),
                new Date().toString(),
                null));
      }

      warehouseTransferRepository.updateStatus(correlationId, logKey, transferId, "APPROVED", userIdentifier);

      auditTrailService.record(
          correlationId,
          logKey,
          AuditAction.UPDATE,
          AuditEntityType.STOCK,
          transferId,
          "Transfer #" + transferId,
          "INITIATED",
          "APPROVED by user " + userIdentifier);

      return findById(correlationId, logKey, transferId);
    } catch (GeneralStatusCodeErrorException e) {
      throw e;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to approve warehouse transfer: " + e.getMessage(),
              "Transfer Approval Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  @Override
  public WarehouseTransferDto completeTransfer(String correlationId, String logKey, long transferId)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      Long userId = SecurityUtils.requireAuthenticatedUserId();
      String userIdentifier = String.valueOf(userId);

      // Lock transfer row to prevent duplicate completion / replay attacks
      WarehouseTransfer transfer =
          warehouseTransferRepository
              .findByIdForUpdate(correlationId, logKey, transferId)
              .orElseThrow(
                  () ->
                      new GeneralStatusCodeErrorException(
                          new ErrorMessage(
                              "Warehouse transfer not found",
                              "Transfer Not Found",
                              HttpStatus.NOT_FOUND,
                              HttpStatusCodeEnum.NOT_FOUND.getValue(),
                              ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                              new Date().toString(),
                              null)));

      if (!"INITIATED".equalsIgnoreCase(transfer.getStatus())
          && !"APPROVED".equalsIgnoreCase(transfer.getStatus())) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Transfer cannot be completed. Current status: " + transfer.getStatus(),
                "Invalid Transfer State",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.STOCK_UPDATE_FAILED.getCode(),
                new Date().toString(),
                null));
      }

      // Lock source and destination warehouse inventory rows deterministically (by warehouse id order to prevent deadlock)
      long lowWhId = Math.min(transfer.getSourceWarehouseId(), transfer.getDestWarehouseId());
      long highWhId = Math.max(transfer.getSourceWarehouseId(), transfer.getDestWarehouseId());

      // Ensure both inventory records exist so they can be locked FOR UPDATE
      ensureInventoryRecord(correlationId, logKey, userId, transfer.getSourceWarehouseId(), transfer.getProductId());
      ensureInventoryRecord(correlationId, logKey, userId, transfer.getDestWarehouseId(), transfer.getProductId());

      // Lock in order
      WarehouseInventory inv1 =
          warehouseInventoryRepository
              .findByWarehouseAndProductForUpdate(correlationId, logKey, lowWhId, transfer.getProductId())
              .orElseThrow();
      WarehouseInventory inv2 =
          warehouseInventoryRepository
              .findByWarehouseAndProductForUpdate(correlationId, logKey, highWhId, transfer.getProductId())
              .orElseThrow();

      WarehouseInventory srcInv =
          (transfer.getSourceWarehouseId() == lowWhId) ? inv1 : inv2;
      WarehouseInventory destInv =
          (transfer.getDestWarehouseId() == lowWhId) ? inv1 : inv2;

      int srcPrev = srcInv.getQty();
      if (srcPrev < transfer.getQty()) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Insufficient stock in source warehouse at completion time. Available: "
                    + srcPrev
                    + ", Requested: "
                    + transfer.getQty(),
                "Insufficient Stock",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.STOCK_UPDATE_FAILED.getCode(),
                new Date().toString(),
                null));
      }

      int destPrev = destInv.getQty();
      int srcNew = srcPrev - transfer.getQty();
      int destNew = destPrev + transfer.getQty();

      // Update both balances
      warehouseInventoryRepository.updateQty(
          correlationId, logKey, transfer.getSourceWarehouseId(), transfer.getProductId(), srcNew);
      warehouseInventoryRepository.updateQty(
          correlationId, logKey, transfer.getDestWarehouseId(), transfer.getProductId(), destNew);

      // Append immutable ledger records for both warehouses
      Timestamp now = Timestamp.valueOf(LocalDateTime.now());
      String transferRef = "TRANSFER-OUT to WH-" + transfer.getDestWarehouseId() + " (Transfer #" + transferId + ")";
      Stock srcTx =
          Stock.builder()
              .userId(userId)
              .warehouseId(transfer.getSourceWarehouseId())
              .productId(transfer.getProductId())
              .type(MovementType.STOCK_OUT.getValue())
              .typeName(MovementType.STOCK_OUT.name())
              .qty(transfer.getQty())
              .previousQty(srcPrev)
              .resultingQty(srcNew)
              .reasonReference(transferRef)
              .createdBy(userIdentifier)
              .createdAt(now)
              .build();
      stockRepository.save(correlationId, logKey, srcTx);

      String destRef = "TRANSFER-IN from WH-" + transfer.getSourceWarehouseId() + " (Transfer #" + transferId + ")";
      Stock destTx =
          Stock.builder()
              .userId(userId)
              .warehouseId(transfer.getDestWarehouseId())
              .productId(transfer.getProductId())
              .type(MovementType.STOCK_IN.getValue())
              .typeName(MovementType.STOCK_IN.name())
              .qty(transfer.getQty())
              .previousQty(destPrev)
              .resultingQty(destNew)
              .reasonReference(destRef)
              .createdBy(userIdentifier)
              .createdAt(now)
              .build();
      stockRepository.save(correlationId, logKey, destTx);

      // Transition transfer status to COMPLETED
      warehouseTransferRepository.updateStatus(correlationId, logKey, transferId, "COMPLETED", userIdentifier);

      // Audit trail
      auditTrailService.record(
          correlationId,
          logKey,
          AuditAction.UPDATE,
          AuditEntityType.STOCK,
          transferId,
          "Transfer #" + transferId,
          "INITIATED",
          "COMPLETED: Moved "
              + transfer.getQty()
              + " pcs from WH-"
              + transfer.getSourceWarehouseId()
              + " to WH-"
              + transfer.getDestWarehouseId());

      return findById(correlationId, logKey, transferId);
    } catch (GeneralStatusCodeErrorException e) {
      throw e;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to complete transfer: " + e.getMessage(),
              "Transfer Completion Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  @Override
  public WarehouseTransferDto cancelTransfer(
      String correlationId, String logKey, long transferId, String reason)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      Long userId = SecurityUtils.requireAuthenticatedUserId();
      String userIdentifier = String.valueOf(userId);

      WarehouseTransfer transfer =
          warehouseTransferRepository
              .findByIdForUpdate(correlationId, logKey, transferId)
              .orElseThrow(
                  () ->
                      new GeneralStatusCodeErrorException(
                          new ErrorMessage(
                              "Warehouse transfer not found",
                              "Transfer Not Found",
                              HttpStatus.NOT_FOUND,
                              HttpStatusCodeEnum.NOT_FOUND.getValue(),
                              ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                              new Date().toString(),
                              null)));

      if ("COMPLETED".equalsIgnoreCase(transfer.getStatus())) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Completed transfers cannot be cancelled",
                "Invalid Transfer State",
                HttpStatus.BAD_REQUEST,
                HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                ErrorMessageCodeEnum.STOCK_UPDATE_FAILED.getCode(),
                new Date().toString(),
                null));
      }

      if ("CANCELLED".equalsIgnoreCase(transfer.getStatus())) {
        return findById(correlationId, logKey, transferId);
      }

      warehouseTransferRepository.updateStatus(correlationId, logKey, transferId, "CANCELLED", userIdentifier);
      return findById(correlationId, logKey, transferId);
    } catch (GeneralStatusCodeErrorException e) {
      throw e;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to cancel transfer: " + e.getMessage(),
              "Transfer Cancellation Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  @Override
  public WarehouseTransferDto findById(String correlationId, String logKey, long id)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      List<WarehouseTransferDto> all = warehouseTransferRepository.findAll(correlationId, logKey);
      return all.stream()
          .filter(t -> t.getId() == id)
          .findFirst()
          .orElseThrow(
              () ->
                  new GeneralStatusCodeErrorException(
                      new ErrorMessage(
                          "Warehouse transfer not found",
                          "Transfer Not Found",
                          HttpStatus.NOT_FOUND,
                          HttpStatusCodeEnum.NOT_FOUND.getValue(),
                          ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                          new Date().toString(),
                          null)));
    } catch (GeneralStatusCodeErrorException e) {
      throw e;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to fetch transfer",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  @Override
  public List<WarehouseTransferDto> findAll(String correlationId, String logKey, String status)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      if (status != null && !status.isBlank()) {
        return warehouseTransferRepository.findByStatus(correlationId, logKey, status.trim().toUpperCase());
      }
      return warehouseTransferRepository.findAll(correlationId, logKey);
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to list transfers",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  private void ensureInventoryRecord(
      String correlationId, String logKey, Long userId, long warehouseId, long productId) throws Exception {
    WarehouseInventory existing =
        warehouseInventoryRepository
            .findByWarehouseAndProduct(correlationId, logKey, warehouseId, productId)
            .orElse(null);
    if (existing == null) {
      warehouseInventoryRepository.save(
          correlationId,
          logKey,
          WarehouseInventory.builder()
              .userId(userId)
              .warehouseId(warehouseId)
              .productId(productId)
              .qty(0)
              .build());
    }
  }
}

