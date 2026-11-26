package com.stockiq.inventory.controller;

import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import com.stockiq.inventory.dto.WarehouseTransferDto;
import com.stockiq.inventory.service.WarehouseTransferService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
public class WarehouseTransferController {

  private static final String MAIN_LOG_KEY = "TRANSFER_";
  private static final String X_CORRELATION_ID = "X-Correlation-Id";

  private final WarehouseTransferService warehouseTransferService;

  public WarehouseTransferController(WarehouseTransferService warehouseTransferService) {
    this.warehouseTransferService = warehouseTransferService;
  }

  @PostMapping
  public ResponseEntity<WarehouseTransferDto> initiateTransfer(
      @RequestBody @Valid WarehouseTransferDto dto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "INITIATE";
    WarehouseTransferDto initiated = warehouseTransferService.initiateTransfer(correlationId, logKey, dto);
    return new ResponseEntity<>(initiated, HttpStatus.CREATED);
  }

  @PostMapping("/{id}/approve")
  public ResponseEntity<WarehouseTransferDto> approveTransfer(
      @PathVariable long id,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "APPROVE";
    WarehouseTransferDto approved = warehouseTransferService.approveTransfer(correlationId, logKey, id);
    return ResponseEntity.ok(approved);
  }

  @PostMapping("/{id}/complete")
  public ResponseEntity<WarehouseTransferDto> completeTransfer(
      @PathVariable long id,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "COMPLETE";
    WarehouseTransferDto completed = warehouseTransferService.completeTransfer(correlationId, logKey, id);
    return ResponseEntity.ok(completed);
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<WarehouseTransferDto> cancelTransfer(
      @PathVariable long id,
      @RequestParam(value = "reason", required = false) String reason,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "CANCEL";
    WarehouseTransferDto cancelled = warehouseTransferService.cancelTransfer(correlationId, logKey, id, reason);
    return ResponseEntity.ok(cancelled);
  }

  @GetMapping("/{id}")
  public ResponseEntity<WarehouseTransferDto> findById(
      @PathVariable long id,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "GET_BY_ID";
    WarehouseTransferDto found = warehouseTransferService.findById(correlationId, logKey, id);
    return ResponseEntity.ok(found);
  }

  @GetMapping
  public ResponseEntity<List<WarehouseTransferDto>> findAll(
      @RequestParam(value = "status", required = false) String status,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "GET_ALL";
    List<WarehouseTransferDto> list = warehouseTransferService.findAll(correlationId, logKey, status);
    return ResponseEntity.ok(list);
  }
}

