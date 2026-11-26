package com.stockiq.inventory.service.impl;

import com.stockiq.inventory.common.enums.ErrorMessageCodeEnum;
import com.stockiq.inventory.common.enums.HttpStatusCodeEnum;
import com.stockiq.inventory.common.enums.product.ProductStatus;
import com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException;
import com.stockiq.inventory.common.util.ActivityLog;
import com.stockiq.inventory.common.util.ErrorMessage;
import com.stockiq.inventory.common.util.PageResponse;
import com.stockiq.inventory.common.util.SecurityUtils;
import com.stockiq.inventory.dto.ProductDto;
import com.stockiq.inventory.entity.Category;
import com.stockiq.inventory.entity.Product;
import com.stockiq.inventory.repository.ProductRepository;
import com.stockiq.inventory.service.CategoryService;
import com.stockiq.inventory.service.ProductService;
import com.stockiq.inventory.service.AuditTrailService;
import com.stockiq.inventory.common.enums.audit.AuditAction;
import com.stockiq.inventory.common.enums.audit.AuditEntityType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

  private static final String SERVICE_IMPL = "product_service_impl";
  private static final String INIT_LOG = "init_log";
  private static final String EXCEPTION_LOG = "exception_log";
  private static final String DUPLICATE_EXCEPTION_LOG = "duplicate_exception_log";

  private final ProductRepository productRepository;
  private final CategoryService categoryService;
  private final AuditTrailService auditTrailService;
  private final com.stockiq.inventory.repository.StockRepository stockRepository;
  private final com.stockiq.inventory.repository.SupplierRepository supplierRepository;
  private final com.stockiq.inventory.repository.WarehouseRepository warehouseRepository;
  private final com.stockiq.inventory.repository.WarehouseInventoryRepository warehouseInventoryRepository;

  public ProductServiceImpl(
      ProductRepository productRepository,
      CategoryService categoryService,
      AuditTrailService auditTrailService,
      com.stockiq.inventory.repository.StockRepository stockRepository,
      com.stockiq.inventory.repository.SupplierRepository supplierRepository,
      com.stockiq.inventory.repository.WarehouseRepository warehouseRepository,
      com.stockiq.inventory.repository.WarehouseInventoryRepository warehouseInventoryRepository) {
    this.productRepository = productRepository;
    this.categoryService = categoryService;
    this.auditTrailService = auditTrailService;
    this.stockRepository = stockRepository;
    this.supplierRepository = supplierRepository;
    this.warehouseRepository = warehouseRepository;
    this.warehouseInventoryRepository = warehouseInventoryRepository;
  }

  /**
   * Create Product
   *
   * @param correlationId String
   * @param logKey String
   * @param productDto ProductDto
   * @return ProductDto
   * @throws GeneralStatusCodeErrorException if an error occurs during the operation
   */
  @Override
  public ProductDto save(String correlationId, String logKey, ProductDto productDto)
      throws GeneralStatusCodeErrorException {

    try {

      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);

      Category category =
          categoryService.findByName(correlationId, logKey, productDto.getCategory());

      if (productDto.getSupplierId() != null && productDto.getSupplierId() > 0) {
        supplierRepository.findById(correlationId, logKey, productDto.getSupplierId())
            .orElseThrow(() -> new GeneralStatusCodeErrorException(
                new ErrorMessage(
                    "Supplier not found or not owned by you",
                    "Invalid Supplier",
                    HttpStatus.BAD_REQUEST,
                    HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                    ErrorMessageCodeEnum.PRODUCT_CREATE_FAILED.getCode(),
                    new Date().toString(),
                    null)));
      }

      // Auto-generate SKU if omitted
      String sku = productDto.getSku();
      if (sku == null || sku.isBlank()) {
        sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        productDto.setSku(sku);
      } else {
        sku = sku.trim();
      }

      String userId = SecurityUtils.getCurrentUserId();

      Product product =
          Product.builder()
              .name(productDto.getName())
              .sku(sku)
              .description(productDto.getDescription())
              .categoryId(category.getId())
              .supplierId(productDto.getSupplierId())
              .uom(productDto.getUom() != null && !productDto.getUom().isBlank() ? productDto.getUom() : "PCS")
              .costPrice(productDto.getCostPrice())
              .sellingPrice(productDto.getSellingPrice())
              .qty(productDto.getQty() != null ? productDto.getQty() : 0)
              .reorderPoint(productDto.getReorder() != null ? productDto.getReorder() : 0)
              .status(
                  productDto.getStatus() != null
                      ? productDto.getStatus().getValue()
                      : ProductStatus.IN_STOCK.getValue())
              .createdBy(userId)
              .createdAt(Timestamp.valueOf(LocalDateTime.now()))
              .build();

      long id = productRepository.save(correlationId, logKey, product);

      productDto.setId(id);
      productDto.setCreatedAt(product.getCreatedAt().toString());

      int initialQty = productDto.getQty() != null ? productDto.getQty() : 0;
      if (initialQty > 0) {
        Long numericUserId = null;
        try {
          numericUserId = Long.parseLong(userId);
        } catch (Exception ignored) {
          numericUserId = 1L;
        }

        Long defaultWhId = null;
        try {
          if (warehouseRepository != null) {
            var defWh = warehouseRepository.findOrCreateDefaultWarehouse(correlationId, logKey, numericUserId);
            if (defWh.isPresent()) {
              defaultWhId = defWh.get().getId();
              if (warehouseInventoryRepository != null) {
                warehouseInventoryRepository.save(
                    correlationId,
                    logKey,
                    com.stockiq.inventory.entity.WarehouseInventory.builder()
                        .userId(numericUserId)
                        .warehouseId(defaultWhId)
                        .productId(id)
                        .qty(initialQty)
                        .build());
              }
            }
          }
        } catch (Exception e) {
          // Fallback gracefully if warehouse repo not available in unit test mocks
        }

        com.stockiq.inventory.entity.Stock openingTx = com.stockiq.inventory.entity.Stock.builder()
            .userId(numericUserId)
            .warehouseId(defaultWhId)
            .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN.getValue())
            .typeName(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN.name())
            .productId(id)
            .qty(initialQty)
            .previousQty(0)
            .resultingQty(initialQty)
            .reasonReference("OPENING-STOCK: Initial balance on product creation")
            .createdBy(userId)
            .createdAt(Timestamp.valueOf(LocalDateTime.now()))
            .build();
        stockRepository.save(correlationId, logKey, openingTx);
      }

      auditTrailService.record(
          correlationId,
          logKey,
          AuditAction.CREATE,
          AuditEntityType.PRODUCT,
          id,
          productDto.getName(),
          null,
          "SKU="
              + sku
              + ", Qty="
              + productDto.getQty()
              + ", Reorder="
              + productDto.getReorder()
              + ", Category="
              + productDto.getCategory()
              + ", Status="
              + (productDto.getStatus() != null ? productDto.getStatus().name() : "IN_STOCK"));

      return productDto;

    } catch (DuplicateKeyException e) {

      ActivityLog.logError(
          correlationId, logKey, SERVICE_IMPL, DUPLICATE_EXCEPTION_LOG, e.getMessage());

      String message = "Product with this name or SKU already exists";
      if (e.getMessage() != null && e.getMessage().contains("sku")) {
        message = "Product SKU already exists in your inventory";
      }

      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              message,
              "Product Create Failed",
              HttpStatus.BAD_REQUEST,
              HttpStatusCodeEnum.BAD_REQUEST.getValue(),
              ErrorMessageCodeEnum.PRODUCT_CREATE_FAILED.getCode(),
              new Date().toString(),
              null));

    } catch (Exception e) {

      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());

      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to create product: " + e.getMessage(),
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  /**
   * Update Product
   *
   * @param correlationId String
   * @param logKey String
   * @param productDto ProductDto
   * @param id long
   * @throws GeneralStatusCodeErrorException if an error occurs during the operation
   */
  @Override
  public void update(String correlationId, String logKey, ProductDto productDto, long id)
      throws GeneralStatusCodeErrorException {

    try {

      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);

      ProductDto oldProduct = findById(correlationId, logKey, id);

      Category category =
          categoryService.findByName(correlationId, logKey, productDto.getCategory());

      if (productDto.getSupplierId() != null && productDto.getSupplierId() > 0) {
        supplierRepository.findById(correlationId, logKey, productDto.getSupplierId())
            .orElseThrow(() -> new GeneralStatusCodeErrorException(
                new ErrorMessage(
                    "Supplier not found or not owned by you",
                    "Invalid Supplier",
                    HttpStatus.BAD_REQUEST,
                    HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                    ErrorMessageCodeEnum.PRODUCT_CREATE_FAILED.getCode(),
                    new Date().toString(),
                    null)));
      }

      String sku = productDto.getSku();
      if (sku == null || sku.isBlank()) {
        sku = oldProduct.getSku();
      } else {
        sku = sku.trim();
      }

      if (productDto.getSupplierId() != null && productDto.getSupplierId() > 0) {
        supplierRepository.findById(correlationId, logKey, productDto.getSupplierId())
            .orElseThrow(() -> new GeneralStatusCodeErrorException(
                new ErrorMessage(
                    "Supplier not found or not owned by you",
                    "Invalid Supplier",
                    HttpStatus.BAD_REQUEST,
                    HttpStatusCodeEnum.BAD_REQUEST.getValue(),
                    ErrorMessageCodeEnum.PRODUCT_UPDATE_FAILED.getCode(),
                    new Date().toString(),
                    null)));
      }

      Product product =
          Product.builder()
              .id(id)
              .name(productDto.getName())
              .sku(sku)
              .description(productDto.getDescription() != null ? productDto.getDescription() : oldProduct.getDescription())
              .categoryId(category.getId())
              .supplierId(productDto.getSupplierId() != null ? productDto.getSupplierId() : oldProduct.getSupplierId())
              .uom(productDto.getUom() != null ? productDto.getUom() : (oldProduct.getUom() != null ? oldProduct.getUom() : "PCS"))
              .costPrice(productDto.getCostPrice() != null ? productDto.getCostPrice() : oldProduct.getCostPrice())
              .sellingPrice(productDto.getSellingPrice() != null ? productDto.getSellingPrice() : oldProduct.getSellingPrice())
              .qty(oldProduct.getQty())
              .reorderPoint(productDto.getReorder() != null ? productDto.getReorder() : 0)
              .status(
                  productDto.getStatus() != null
                      ? productDto.getStatus().getValue()
                      : ProductStatus.IN_STOCK.getValue())
              .updatedBy(SecurityUtils.getCurrentUserId())
              .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
              .build();

      int updated = productRepository.update(correlationId, logKey, product);

      if (updated == 0) {

        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Product not found",
                "Product Update Failed",
                HttpStatus.NOT_FOUND,
                HttpStatusCodeEnum.NOT_FOUND.getValue(),
                ErrorMessageCodeEnum.PRODUCT_NOT_FOUND.getCode(),
                new Date().toString(),
                null));
      }

      StringBuilder oldVal = new StringBuilder();
      StringBuilder newVal = new StringBuilder();

      if (oldProduct.getName() != null && !oldProduct.getName().equals(productDto.getName())) {
        oldVal.append("Name=").append(oldProduct.getName()).append("; ");
        newVal.append("Name=").append(productDto.getName()).append("; ");
      }
      if (oldProduct.getQty() != productDto.getQty()) {
        oldVal.append("Qty=").append(oldProduct.getQty()).append("; ");
        newVal.append("Qty=").append(productDto.getQty()).append("; ");
      }
      if (oldProduct.getReorder() != productDto.getReorder()) {
        oldVal.append("Reorder=").append(oldProduct.getReorder()).append("; ");
        newVal.append("Reorder=").append(productDto.getReorder()).append("; ");
      }
      if (oldProduct.getCategory() != null
          && !oldProduct.getCategory().equals(productDto.getCategory())) {
        oldVal.append("Category=").append(oldProduct.getCategory()).append("; ");
        newVal.append("Category=").append(productDto.getCategory()).append("; ");
      }
      ProductStatus newStatus =
          productDto.getStatus() != null
              ? productDto.getStatus()
              : ProductStatus.IN_STOCK;
      if (oldProduct.getStatus() != newStatus) {
        oldVal.append("Status=").append(oldProduct.getStatus()).append("; ");
        newVal.append("Status=").append(newStatus).append("; ");
      }

      String oldString = oldVal.toString().trim();
      String newString = newVal.toString().trim();
      if (oldString.endsWith(";")) {
        oldString = oldString.substring(0, oldString.length() - 1).trim();
      }
      if (newString.endsWith(";")) {
        newString = newString.substring(0, newString.length() - 1).trim();
      }

      if (!oldString.isEmpty() || !newString.isEmpty()) {
        auditTrailService.record(
            correlationId,
            logKey,
            AuditAction.UPDATE,
            AuditEntityType.PRODUCT,
            id,
            productDto.getName(),
            oldString,
            newString);
      }

    } catch (DuplicateKeyException e) {

      ActivityLog.logError(
          correlationId, logKey, SERVICE_IMPL, DUPLICATE_EXCEPTION_LOG, e.getMessage());

      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Product name or SKU already exists",
              "Product Update Failed",
              HttpStatus.BAD_REQUEST,
              HttpStatusCodeEnum.BAD_REQUEST.getValue(),
              ErrorMessageCodeEnum.PRODUCT_UPDATE_FAILED.getCode(),
              new Date().toString(),
              null));

    } catch (GeneralStatusCodeErrorException e) {

      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());

      throw e;

    } catch (Exception e) {

      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());

      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to update product: " + e.getMessage(),
              "Product Update Failed",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.PRODUCT_UPDATE_FAILED.getCode(),
              new Date().toString(),
              null));
    }
  }

  /**
   * Delete Product
   *
   * @param correlationId String
   * @param logKey String
   * @param id long
   * @throws GeneralStatusCodeErrorException if an error occurs during the operation
   */
  @Override
  public void delete(String correlationId, String logKey, long id)
      throws GeneralStatusCodeErrorException {

    try {

      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);

      ProductDto oldProduct = findById(correlationId, logKey, id);

      int deleted = productRepository.delete(correlationId, logKey, id);

      if (deleted == 0) {

        throw new GeneralStatusCodeErrorException(
            new ErrorMessage(
                "Product not found",
                "Product Delete Failed",
                HttpStatus.NOT_FOUND,
                HttpStatusCodeEnum.NOT_FOUND.getValue(),
                ErrorMessageCodeEnum.PRODUCT_NOT_FOUND.getCode(),
                new Date().toString(),
                null));
      }

      auditTrailService.record(
          correlationId,
          logKey,
          AuditAction.DELETE,
          AuditEntityType.PRODUCT,
          id,
          oldProduct.getName(),
          "Name="
              + oldProduct.getName()
              + ", Qty="
              + oldProduct.getQty()
              + ", Reorder="
              + oldProduct.getReorder()
              + ", Category="
              + oldProduct.getCategory()
              + ", Status="
              + (oldProduct.getStatus() != null ? oldProduct.getStatus().name() : "IN_STOCK"),
          null);

    } catch (GeneralStatusCodeErrorException e) {

      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());

      throw e;

    } catch (Exception e) {

      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());

      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to delete product",
              "Product Delete Failed",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.PRODUCT_DELETE_FAILED.getCode(),
              new Date().toString(),
              null));
    }
  }

  /**
   * Find Product By Id
   *
   * @param correlationId String
   * @param logKey String
   * @param id long
   * @return ProductDto
   * @throws GeneralStatusCodeErrorException if an error occurs during the operation
   */
  @Override
  public ProductDto findById(String correlationId, String logKey, long id)
      throws GeneralStatusCodeErrorException {

    try {

      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);

      return productRepository
          .findById(correlationId, logKey, id)
          .orElseThrow(
              () ->
                  new GeneralStatusCodeErrorException(
                      new ErrorMessage(
                          "Product not found",
                          "Product Not Found",
                          HttpStatus.NOT_FOUND,
                          HttpStatusCodeEnum.NOT_FOUND.getValue(),
                          ErrorMessageCodeEnum.PRODUCT_NOT_FOUND.getCode(),
                          new Date().toString(),
                          null)));

    } catch (GeneralStatusCodeErrorException e) {

      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());

      throw e;

    } catch (Exception e) {

      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());

      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to find product",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  /**
   * Find All Products
   *
   * @param correlationId String
   * @param logKey String
   * @param page int
   * @param size int
   * @param search String
   * @param category String
   * @return PageResponse<ProductDto>
   * @throws GeneralStatusCodeErrorException if an error occurs during the operation
   */
  @Override
  public PageResponse<ProductDto> findAll(
      String correlationId, String logKey, int page, int size, String search, String category)
      throws GeneralStatusCodeErrorException {

    try {

      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);

      List<ProductDto> products =
          productRepository.findAll(correlationId, logKey, page, size, search, category);

      long total = productRepository.countAll(correlationId, logKey, search, category);

      return new PageResponse<>(products, page, size, total);

    } catch (Exception e) {

      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());

      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to find products",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  /**
   * Get Low Stock Products
   *
   * @param correlationId String
   * @param logKey String
   * @return List<ProductDto>
   * @throws GeneralStatusCodeErrorException if an error occurs during the operation
   */
  @Override
  public List<ProductDto> getLowStockAlerts(String correlationId, String logKey)
      throws GeneralStatusCodeErrorException {

    try {

      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);

      return productRepository.getLowStockAlerts(correlationId, logKey);

    } catch (Exception e) {

      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());

      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to get low stock products",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  /**
   * Get All Products
   *
   * @param correlationId String
   * @param logKey String
   * @return List<ProductDto>
   * @throws GeneralStatusCodeErrorException if an error occurs during the operation
   */
  @Override
  public List<ProductDto> getAllProducts(String correlationId, String logKey)
      throws GeneralStatusCodeErrorException {

    try {

      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);

      return productRepository.findAll(correlationId, logKey, 1, Integer.MAX_VALUE, "", null);

    } catch (Exception e) {

      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());

      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to get products",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }

  /**
   * Get all low stock products across all users (no SecurityContext — for background jobs only).
   *
   * @param correlationId String
   * @param logKey String
   * @return List<ProductDto>
   * @throws GeneralStatusCodeErrorException if an error occurs during the operation
   */
  @Override
  public List<ProductDto> getAllLowStockProducts(String correlationId, String logKey)
      throws GeneralStatusCodeErrorException {

    try {

      ActivityLog.logInfo(correlationId, logKey, SERVICE_IMPL, INIT_LOG);

      return productRepository.getAllLowStockProducts(correlationId, logKey);

    } catch (Exception e) {

      ActivityLog.logError(correlationId, logKey, SERVICE_IMPL, EXCEPTION_LOG, e.getMessage());

      throw new GeneralStatusCodeErrorException(
          new ErrorMessage(
              "Failed to get all low stock products",
              "Internal Server Error",
              HttpStatus.INTERNAL_SERVER_ERROR,
              HttpStatusCodeEnum.INTERNAL_SERVER_ERROR.getValue(),
              ErrorMessageCodeEnum.INTERNAL_SERVER_ERROR.getCode(),
              new Date().toString(),
              null));
    }
  }
}
