package com.stockiq.inventory.service.impl;

import com.stockiq.inventory.common.enums.ErrorMessageCodeEnum;
import com.stockiq.inventory.common.enums.HttpStatusCodeEnum;
import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.common.util.ErrorMessage;
import com.stockiq.inventory.common.util.SecurityUtils;
import com.stockiq.inventory.dto.WarehouseDto;
import com.stockiq.inventory.dto.WarehouseInventoryDto;
import com.stockiq.inventory.entity.Warehouse;
import com.stockiq.inventory.repository.WarehouseInventoryRepository;
import com.stockiq.inventory.repository.WarehouseRepository;
import com.stockiq.inventory.service.WarehouseService;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WarehouseServiceImpl implements WarehouseService {

  private static final String SERVICE_IMPL = "warehouse_service_impl";
  private static final String INIT_LOG = "init_log";
  private static final String EXCEPTION_LOG = "exception_log";

  private final WarehouseRepository warehouseRepository;
  private final WarehouseInventoryRepository warehouseInventoryRepository;

  public WarehouseServiceImpl(
      WarehouseRepository warehouseRepository,
      WarehouseInventoryRepository warehouseInventoryRepository) {
    this.warehouseRepository = warehouseRepository;
    this.warehouseInventoryRepository = warehouseInventoryRepository;
  }

  @Override
  public WarehouseDto create(String correlationId, String logKey, WarehouseDto dto)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      Long userId = SecurityUtils.requireAuthenticatedUserId();

      Warehouse warehouse =
          Warehouse.builder()
              .userId(userId)
              .code(dto.getCode().trim().toUpperCase())
              .name(dto.getName().trim())
              .description(dto.getDescription())
              .address(dto.getAddress())
              .city(dto.getCity())
              .state(dto.getState())
              .postalCode(dto.getPostalCode())
              .active(dto.getActive() != null ? dto.getActive() : true)
              .build();

      long id = warehouseRepository.save(correlationId, logKey, warehouse);
      dto.setId(id);
      dto.setCode(warehouse.getCode());
      dto.setActive(warehouse.isActive());
      return dto;
    } catch (DuplicateKeyException e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Warehouse code already exists: " + dto.getCode(),
              "Duplicate Warehouse Code",
              HttpStatus.BAD_REQUEST,
              HttpStatusCodeEnum.BAD_REQUEST.getValue(),
              ErrorMessageCodeEnum.VALIDATION_ERROR.getCode(),
              new Date().toString(),
              null));
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to create warehouse: " + e.getMessage(),
              "Warehouse Creation Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  @Override
  public WarehouseDto update(String correlationId, String logKey, long id, WarehouseDto dto)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      Warehouse existing =
          warehouseRepository
              .findById(correlationId, logKey, id)
              .orElseThrow(
                  () ->
                      new GeneralStatusCodeErrorException(
                          new ErrorMessage(
                              "Warehouse not found",
                              "Warehouse Not Found",
                              HttpStatus.NOT_FOUND,
                              HttpStatusCodeEnum.NOT_FOUND.getValue(),
                              ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                              new Date().toString(),
                              null)));

      existing.setName(dto.getName() != null ? dto.getName().trim() : existing.getName());
      existing.setDescription(dto.getDescription() != null ? dto.getDescription() : existing.getDescription());
      existing.setAddress(dto.getAddress() != null ? dto.getAddress() : existing.getAddress());
      existing.setCity(dto.getCity() != null ? dto.getCity() : existing.getCity());
      existing.setState(dto.getState() != null ? dto.getState() : existing.getState());
      existing.setPostalCode(dto.getPostalCode() != null ? dto.getPostalCode() : existing.getPostalCode());
      if (dto.getActive() != null) {
        existing.setActive(dto.getActive());
      }

      warehouseRepository.update(correlationId, logKey, existing);
      return mapToDto(existing);
    } catch (GeneralStatusCodeErrorException e) {
      throw e;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to update warehouse",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  @Override
  public WarehouseDto findById(String correlationId, String logKey, long id)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      Warehouse warehouse =
          warehouseRepository
              .findById(correlationId, logKey, id)
              .orElseThrow(
                  () ->
                      new GeneralStatusCodeErrorException(
                          new ErrorMessage(
                              "Warehouse not found",
                              "Warehouse Not Found",
                              HttpStatus.NOT_FOUND,
                              HttpStatusCodeEnum.NOT_FOUND.getValue(),
                              ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                              new Date().toString(),
                              null)));
      return mapToDto(warehouse);
    } catch (GeneralStatusCodeErrorException e) {
      throw e;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to fetch warehouse",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  @Override
  public List<WarehouseDto> findAll(String correlationId, String logKey)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      return warehouseRepository.findAll(correlationId, logKey).stream()
          .map(this::mapToDto)
          .collect(Collectors.toList());
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to fetch warehouses",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  @Override
  public void deactivate(String correlationId, String logKey, long id)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      int rows = warehouseRepository.deactivate(correlationId, logKey, id);
      if (rows == 0) {
        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Warehouse not found or already deactivated",
                "Not Found",
                HttpStatus.NOT_FOUND,
                HttpStatusCodeEnum.NOT_FOUND.getValue(),
                ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                new Date().toString(),
                null));
      }
    } catch (GeneralStatusCodeErrorException e) {
      throw e;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to deactivate warehouse",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  @Override
  public List<WarehouseInventoryDto> getInventoryByWarehouse(
      String correlationId, String logKey, long warehouseId)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      // Validate warehouse ownership
      warehouseRepository
          .findById(correlationId, logKey, warehouseId)
          .orElseThrow(
              () ->
                  new GeneralStatusCodeErrorException(
                      new ErrorMessage(
                          "Warehouse not found",
                          "Warehouse Not Found",
                          HttpStatus.NOT_FOUND,
                          HttpStatusCodeEnum.NOT_FOUND.getValue(),
                          ErrorMessageCodeEnum.DATA_NOT_FOUND.getCode(),
                          new Date().toString(),
                          null)));

      return warehouseInventoryRepository.findByWarehouseId(correlationId, logKey, warehouseId);
    } catch (GeneralStatusCodeErrorException e) {
      throw e;
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to fetch warehouse inventory",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  @Override
  public List<WarehouseInventoryDto> getInventoryByProduct(
      String correlationId, String logKey, long productId)
      throws GeneralStatusCodeErrorException {
    try {
      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);
      return warehouseInventoryRepository.findByProductId(correlationId, logKey, productId);
    } catch (Exception e) {
      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());
      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to fetch product warehouse inventory",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  private WarehouseDto mapToDto(Warehouse w) {
    return WarehouseDto.builder()
        .id(w.getId())
        .code(w.getCode())
        .name(w.getName())
        .description(w.getDescription())
        .address(w.getAddress())
        .city(w.getCity())
        .state(w.getState())
        .postalCode(w.getPostalCode())
        .active(w.isActive())
        .createdAt(w.getCreatedAt() != null ? w.getCreatedAt().toString() : null)
        .updatedAt(w.getUpdatedAt() != null ? w.getUpdatedAt().toString() : null)
        .build();
  }
}

