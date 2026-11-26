package com.stockiq.inventory.controller;

import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import com.stockiq.inventory.dto.WarehouseDto;
import com.stockiq.inventory.dto.WarehouseInventoryDto;
import com.stockiq.inventory.service.WarehouseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/warehouses")
public class WarehouseController {

  private static final String MAIN_LOG_KEY = "WAREHOUSE_";
  private static final String X_CORRELATION_ID = "X-Correlation-Id";

  private final WarehouseService warehouseService;

  public WarehouseController(WarehouseService warehouseService) {
    this.warehouseService = warehouseService;
  }

  @PostMapping
  public ResponseEntity<WarehouseDto> create(
      @RequestBody @Valid WarehouseDto dto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "CREATE";
    WarehouseDto created = warehouseService.create(correlationId, logKey, dto);
    return new ResponseEntity<>(created, HttpStatus.CREATED);
  }

  @PutMapping("/{id}")
  public ResponseEntity<WarehouseDto> update(
      @PathVariable long id,
      @RequestBody WarehouseDto dto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "UPDATE";
    WarehouseDto updated = warehouseService.update(correlationId, logKey, id, dto);
    return ResponseEntity.ok(updated);
  }

  @GetMapping("/{id}")
  public ResponseEntity<WarehouseDto> findById(
      @PathVariable long id,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "GET_BY_ID";
    WarehouseDto found = warehouseService.findById(correlationId, logKey, id);
    return ResponseEntity.ok(found);
  }

  @GetMapping
  public ResponseEntity<List<WarehouseDto>> findAll(
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "GET_ALL";
    List<WarehouseDto> list = warehouseService.findAll(correlationId, logKey);
    return ResponseEntity.ok(list);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(
      @PathVariable long id,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "DEACTIVATE";
    warehouseService.deactivate(correlationId, logKey, id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/inventory")
  public ResponseEntity<List<WarehouseInventoryDto>> getInventory(
      @PathVariable long id,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {
    String logKey = MAIN_LOG_KEY + "GET_INVENTORY";
    List<WarehouseInventoryDto> list = warehouseService.getInventoryByWarehouse(correlationId, logKey, id);
    return ResponseEntity.ok(list);
  }
}

