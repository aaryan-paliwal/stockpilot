package com.stockiq.inventory.controller;

import com.stockiq.inventory.common.enums.HttpStatusCodeEnum;
import com.stockiq.inventory.common.enums.stock.MovementType;
import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import com.stockiq.inventory.common.response.stock.LogMovementResponse;
import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.common.util.SecurityUtils;
import com.stockiq.inventory.dto.ProductDto;
import com.stockiq.inventory.dto.StockDto;
import com.stockiq.inventory.service.StockService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stock")
public class StockController {

  private static final String MAIN_LOG_KEY = "STOCK_";
  private static final String X_CORRELATION_ID = "X-Correlation-Id";
  private static final String X_USER_EMAIL = "X-User-Email";
  private static final String INIT_LOG = "init_log";
  private static final String CONTROLLER = "controller";

  private final StockService stockService;

  public StockController(StockService stockService) {
    this.stockService = stockService;
  }

  /**
   * Log a stock movement (STOCK_IN, STOCK_OUT, ADJUSTMENT).
   *
   * @param movementDto StockMovementDto
   * @param correlationId String
   * @param userEmail String - the logged-in user's email from request header
   * @return ResponseEntity<LogMovementResponse>
   */
  @PostMapping("/move")
  public ResponseEntity<LogMovementResponse> logMovement(
      @RequestBody @Valid StockDto movementDto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId,
      @RequestHeader(value = X_USER_EMAIL, defaultValue = "system") String userEmail)
      throws GeneralStatusCodeErrorException {

    String logKey = MAIN_LOG_KEY + "LOG_MOVEMENT";

    ActivityLog.logInfo(correlationId, logKey, CONTROLLER, INIT_LOG);

    String currentUserId = SecurityUtils.getCurrentUserId();
    String finalUserId = (currentUserId != null) ? currentUserId : "0";

    StockDto result = stockService.logMovement(correlationId, logKey, movementDto, finalUserId);

    return ResponseEntity.status(HttpStatusCode.valueOf(HttpStatusCodeEnum.CREATE.getValue()))
        .body(toResponse(result));
  }

  /**
   * Convenience endpoint for stock-in.
   */
  @PostMapping("/in")
  public ResponseEntity<LogMovementResponse> stockIn(
      @RequestBody @Valid StockDto movementDto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    movementDto.setType(MovementType.STOCK_IN);
    return logMovement(movementDto, correlationId, "system");
  }

  /**
   * Convenience endpoint for stock-out.
   */
  @PostMapping("/out")
  public ResponseEntity<LogMovementResponse> stockOut(
      @RequestBody @Valid StockDto movementDto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    movementDto.setType(MovementType.STOCK_OUT);
    return logMovement(movementDto, correlationId, "system");
  }

  /**
   * Convenience endpoint for stock adjustment.
   */
  @PostMapping("/adjust")
  public ResponseEntity<LogMovementResponse> adjust(
      @RequestBody @Valid StockDto movementDto,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    movementDto.setType(MovementType.ADJUSTMENT);
    return logMovement(movementDto, correlationId, "system");
  }

  /**
   * Get full stock movement history for the authenticated user.
   *
   * @param correlationId String
   * @return ResponseEntity<List<StockDto>>
   */
  @GetMapping("/history")
  public ResponseEntity<List<StockDto>> getHistory(
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    String logKey = MAIN_LOG_KEY + "GET_HISTORY";

    ActivityLog.logInfo(correlationId, logKey, CONTROLLER, INIT_LOG);

    List<StockDto> history = stockService.getHistory(correlationId, logKey);

    return ResponseEntity.status(HttpStatusCode.valueOf(HttpStatusCodeEnum.OK.getValue()))
        .body(history);
  }

  /**
   * Get stock movement history for a specific product.
   */
  @GetMapping("/history/{productId}")
  public ResponseEntity<List<StockDto>> getHistoryByProduct(
      @PathVariable("productId") long productId,
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    String logKey = MAIN_LOG_KEY + "GET_PRODUCT_HISTORY";

    ActivityLog.logInfo(correlationId, logKey, CONTROLLER, INIT_LOG);

    List<StockDto> history = stockService.getHistoryByProduct(correlationId, logKey, productId);

    return ResponseEntity.status(HttpStatusCode.valueOf(HttpStatusCodeEnum.OK.getValue()))
        .body(history);
  }

  /**
   * Get all products currently at or below their reorder point.
   *
   * @param correlationId String
   * @return ResponseEntity<List<ProductDto>>
   */
  @GetMapping("/alerts")
  public ResponseEntity<List<ProductDto>> getLowStockAlerts(
      @RequestHeader(value = X_CORRELATION_ID, defaultValue = "") String correlationId)
      throws GeneralStatusCodeErrorException {

    String logKey = MAIN_LOG_KEY + "GET_LOW_STOCK_ALERTS";

    ActivityLog.logInfo(correlationId, logKey, CONTROLLER, INIT_LOG);

    List<ProductDto> products = stockService.getLowStockAlerts(correlationId, logKey);

    return ResponseEntity.status(HttpStatusCode.valueOf(HttpStatusCodeEnum.OK.getValue()))
        .body(products);
  }

  private LogMovementResponse toResponse(StockDto dto) {
    return LogMovementResponse.builder()
        .id(dto.getId())
        .warehouseId(dto.getWarehouseId())
        .warehouseCode(dto.getWarehouseCode())
        .type(dto.getType())
        .product(dto.getProduct())
        .productId(dto.getProductId())
        .sku(dto.getSku())
        .qty(dto.getQty())
        .previousQty(dto.getPreviousQty())
        .resultingQty(dto.getResultingQty())
        .deltaQty(dto.getDeltaQty())
        .reasonReference(dto.getReasonReference())
        .date(dto.getDate())
        .user(dto.getUser())
        .build();
  }
}
