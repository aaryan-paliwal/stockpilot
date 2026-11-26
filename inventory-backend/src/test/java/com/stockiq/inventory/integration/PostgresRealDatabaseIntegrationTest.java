package com.stockiq.inventory.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.stockiq.inventory.common.util.CustomUserPrincipal;
import com.stockiq.inventory.dto.CategoryDto;
import com.stockiq.inventory.dto.ProductDto;
import com.stockiq.inventory.dto.ReportDto;
import com.stockiq.inventory.dto.SupplierDto;
import com.stockiq.inventory.entity.Category;
import com.stockiq.inventory.entity.PasswordResetToken;
import com.stockiq.inventory.entity.Product;
import com.stockiq.inventory.entity.Supplier;
import com.stockiq.inventory.entity.User;
import com.stockiq.inventory.repository.CategoryRepository;
import com.stockiq.inventory.repository.PasswordResetTokenRepository;
import com.stockiq.inventory.repository.ProductRepository;
import com.stockiq.inventory.repository.ReportRepository;
import com.stockiq.inventory.repository.StockRepository;
import com.stockiq.inventory.repository.SupplierRepository;
import com.stockiq.inventory.repository.UserRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DisplayName("Real PostgreSQL Integration Tests - Full Stack DB & Multi-User Verification")
class PostgresRealDatabaseIntegrationTest {

  private static final String CORR_ID = "pg-test-corr-id";
  private static final String LOG_KEY = "pg-test-log-key";

  @Autowired private DataSource dataSource;
  @Autowired private UserRepository userRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private SupplierRepository supplierRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private StockRepository stockRepository;
  @Autowired private ReportRepository reportRepository;
  @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private com.stockiq.inventory.service.ProductService productService;
  @Autowired private com.stockiq.inventory.service.StockService stockService;
  @Autowired private com.stockiq.inventory.repository.WarehouseRepository warehouseRepository;
  @Autowired private com.stockiq.inventory.repository.WarehouseInventoryRepository warehouseInventoryRepository;
  @Autowired private com.stockiq.inventory.repository.WarehouseTransferRepository warehouseTransferRepository;
  @Autowired private com.stockiq.inventory.service.WarehouseService warehouseService;
  @Autowired private com.stockiq.inventory.service.WarehouseTransferService warehouseTransferService;
  @Autowired private com.stockiq.inventory.repository.ProductBatchRepository productBatchRepository;
  @Autowired private com.stockiq.inventory.repository.BatchInventoryRepository batchInventoryRepository;

  @BeforeEach
  void ensureDatabaseMigrated() {
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/postgresql")
            .baselineOnMigrate(true)
            .load();
    flyway.migrate();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void setAuthenticatedUser(long userId, String email, String role) {
    CustomUserPrincipal principal = new CustomUserPrincipal(email, userId);
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            principal, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private long createTestUser(String email, String name) throws Exception {
    User user =
        User.builder()
            .fullName(name)
            .email(email)
            .password("hashed_password_123")
            .roleId(2L)
            .build();
    long id = userRepository.save(CORR_ID, LOG_KEY, user);
    user.setId(id);
    user.setUserId(id);
    userRepository.update(CORR_ID, LOG_KEY, user);
    return id;
  }

  @Test
  @DisplayName("1. Full CRUD, reporting and constraints on Real PostgreSQL")
  void testCompleteWorkflowOnPostgreSql() throws Exception {
    String uniqueEmail = "pguser_" + System.currentTimeMillis() + "@stockpilot.com";

    // 1. User creation and ID generation via IDENTITY
    User user =
        User.builder()
            .fullName("Postgres Test User")
            .email(uniqueEmail)
            .password("hashed_password_123")
            .roleId(2L) // USER role inserted by V3
            .build();

    long userId = userRepository.save(CORR_ID, LOG_KEY, user);
    assertTrue(userId > 0, "User ID should be generated by PostgreSQL IDENTITY");
    user.setId(userId);
    user.setUserId(userId);
    userRepository.update(CORR_ID, LOG_KEY, user);

    Optional<User> fetchedUser = userRepository.findByEmail(CORR_ID, LOG_KEY, uniqueEmail);
    assertTrue(fetchedUser.isPresent());
    assertEquals("Postgres Test User", fetchedUser.get().getFullName());

    // 2. Category creation
    Category category =
        Category.builder()
            .userId(userId)
            .name("Electronics")
            .icon("cpu")
            .status(1)
            .build();
    long categoryId = categoryRepository.save(CORR_ID, LOG_KEY, category);
    assertTrue(categoryId > 0, "Category ID generated by PostgreSQL");

    // 3. Supplier creation
    Supplier supplier =
        Supplier.builder()
            .userId(userId)
            .name("Acme Electronics Corp")
            .category("Electronics")
            .status("Active")
            .email("contact@acme.com")
            .phone("+1234567890")
            .build();
    long supplierId = supplierRepository.save(CORR_ID, LOG_KEY, supplier);
    assertTrue(supplierId > 0, "Supplier ID generated by PostgreSQL");

    // 4. Product creation with foreign key to category
    Product product =
        Product.builder()
            .userId(userId)
            .name("Industrial Sensor X100")
            .categoryId(categoryId)
            .qty(5)
            .reorderPoint(10) // qty <= reorderPoint -> Low Stock
            .build();
    long productId = productRepository.save(CORR_ID, LOG_KEY, product);
    assertTrue(productId > 0, "Product ID generated by PostgreSQL");

    // 5. Stock movement transaction
    com.stockiq.inventory.entity.Stock stockIn =
        com.stockiq.inventory.entity.Stock.builder()
            .userId(userId)
            .type(1) // IN
            .productId(productId)
            .qty(20)
            .createdBy(uniqueEmail)
            .build();
    long stockTxId = stockRepository.save(CORR_ID, LOG_KEY, stockIn);
    assertTrue(stockTxId > 0, "Stock transaction recorded in PostgreSQL");

    // 6. Reports using PostgreSQL STRING_AGG
    List<ReportDto.InventoryReportItem> invReport =
        reportRepository.getInventoryReport(CORR_ID, LOG_KEY, userId);
    assertFalse(invReport.isEmpty(), "Inventory report query succeeds with STRING_AGG");
    assertEquals("Acme Electronics Corp", invReport.get(0).getSupplierNames());

    List<ReportDto.LowStockReportItem> lowStockReport =
        reportRepository.getLowStockReport(CORR_ID, LOG_KEY, userId);
    assertFalse(lowStockReport.isEmpty(), "Low stock report query succeeds");
    assertEquals("Industrial Sensor X100", lowStockReport.get(0).getProductName());

    // 7. Password Reset Token Persistence in PostgreSQL
    String tokenHash = "sha256_mock_hash_" + System.currentTimeMillis();
    PasswordResetToken token =
        PasswordResetToken.builder()
            .tokenHash(tokenHash)
            .email(uniqueEmail)
            .expiresAt(Instant.now().plusSeconds(900))
            .used(false)
            .build();

    passwordResetTokenRepository.save(token);

    Optional<PasswordResetToken> tokenLookup =
        passwordResetTokenRepository.findByTokenHash(tokenHash);
    assertTrue(tokenLookup.isPresent(), "Token hash must be persisted in PostgreSQL");
    assertFalse(tokenLookup.get().isUsed(), "Token must initially be unused");

    // Atomic invalidation check
    passwordResetTokenRepository.markAsUsed(tokenHash);
    Optional<PasswordResetToken> usedLookup =
        passwordResetTokenRepository.findByTokenHash(tokenHash);
    assertTrue(usedLookup.isPresent());
    assertTrue(usedLookup.get().isUsed(), "Token must be marked as used");

    // Single-use: invalidating email invalidates tokens
    passwordResetTokenRepository.invalidateAllForEmail(uniqueEmail);
  }

  @Test
  @DisplayName("2. Multi-user isolation negative test - User B cannot read or modify User A records")
  void testMultiUserDataIsolation() throws Exception {
    long userAId = createTestUser("userA_" + System.currentTimeMillis() + "@test.com", "User A");
    long userBId = createTestUser("userB_" + System.currentTimeMillis() + "@test.com", "User B");

    // Create record belonging to User A
    setAuthenticatedUser(userAId, "userA@test.com", "USER");
    Category catUserA =
        Category.builder()
            .userId(userAId)
            .name("Secret Dept A " + System.currentTimeMillis())
            .icon("lock")
            .status(1)
            .build();
    long catAId = categoryRepository.save(CORR_ID, LOG_KEY, catUserA);
    assertTrue(catAId > 0);

    // Switch context to User B
    setAuthenticatedUser(userBId, "userB@test.com", "USER");

    // 1. User B attempts to read User A's category by ID -> must return empty
    Optional<CategoryDto> readAttempt = categoryRepository.findById(CORR_ID, LOG_KEY, catAId);
    assertTrue(readAttempt.isEmpty(), "User B MUST NOT be able to read User A's category");

    // 2. User B attempts to update User A's category -> must affect 0 rows
    Category updateAttempt =
        Category.builder()
            .id(catAId)
            .userId(userBId)
            .name("Hijacked Name")
            .icon("hacked")
            .status(1)
            .build();
    int rowsUpdated = categoryRepository.update(CORR_ID, LOG_KEY, updateAttempt);
    assertEquals(0, rowsUpdated, "User B MUST NOT be able to update User A's category (0 rows updated)");

    // 3. User B attempts to delete User A's category -> must affect 0 rows
    int rowsDeleted = categoryRepository.delete(CORR_ID, LOG_KEY, catAId);
    assertEquals(0, rowsDeleted, "User B MUST NOT be able to delete User A's category (0 rows deleted)");

    // 4. Verify User A's record remains untouched
    setAuthenticatedUser(userAId, "userA@test.com", "USER");
    Optional<CategoryDto> catAStillExists = categoryRepository.findById(CORR_ID, LOG_KEY, catAId);
    assertTrue(catAStillExists.isPresent(), "User A category must still exist and be intact");
    assertEquals("Secret Dept A " + (catAStillExists.get().getName().replace("Secret Dept A ", "")), catAStillExists.get().getName());
  }

  @Test
  @DisplayName("3. Foreign key constraint enforcement in PostgreSQL")
  void testForeignKeyConstraintEnforcement() {
    setAuthenticatedUser(555L, "fkuser@test.com", "USER");

    // Attempt to insert product referencing non-existent category ID (999999L)
    Product invalidProduct =
        Product.builder()
            .userId(555L)
            .name("Orphan Product")
            .categoryId(999999L)
            .qty(10)
            .reorderPoint(5)
            .build();

    assertThrows(
        DataIntegrityViolationException.class,
        () -> productRepository.save(CORR_ID, LOG_KEY, invalidProduct),
        "PostgreSQL must reject products referencing a non-existent category_id via FOREIGN KEY constraint");
  }

  @Test
  @DisplayName("4. Unique constraint per user enforcement in PostgreSQL")
  void testUniqueConstraintPerUserEnforcement() throws Exception {
    long user1 = createTestUser("user1_" + System.currentTimeMillis() + "@test.com", "User 1");
    long user2 = createTestUser("user2_" + System.currentTimeMillis() + "@test.com", "User 2");
    String sharedName = "Common Category " + System.currentTimeMillis();

    setAuthenticatedUser(user1, "u1@test.com", "USER");
    Category cat1 = Category.builder().userId(user1).name(sharedName).icon("icon1").status(1).build();
    long id1 = categoryRepository.save(CORR_ID, LOG_KEY, cat1);
    assertTrue(id1 > 0);

    // Duplicate name for SAME user must fail with DuplicateKeyException
    Category duplicateCat = Category.builder().userId(user1).name(sharedName).icon("icon2").status(1).build();
    assertThrows(
        DuplicateKeyException.class,
        () -> categoryRepository.save(CORR_ID, LOG_KEY, duplicateCat),
        "PostgreSQL must enforce unique constraint (uq_categories_name_user) per user");

    // SAME name for DIFFERENT user must succeed
    setAuthenticatedUser(user2, "u2@test.com", "USER");
    Category cat2 = Category.builder().userId(user2).name(sharedName).icon("icon3").status(1).build();
    long id2 = categoryRepository.save(CORR_ID, LOG_KEY, cat2);
    assertTrue(id2 > 0, "Same category name for different user must succeed");
  }

  @Test
  @DisplayName("5. Transactional rollback test in PostgreSQL")
  void testTransactionRollback() throws Exception {
    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
    def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    TransactionStatus status = transactionManager.getTransaction(def);

    long txUserId = createTestUser("tx_" + System.currentTimeMillis() + "@test.com", "Tx User");
    String tempCategoryName = "Rollback Category " + System.currentTimeMillis();
    Category category =
        Category.builder()
            .userId(txUserId)
            .name(tempCategoryName)
            .icon("temp")
            .status(1)
            .build();

    categoryRepository.save(CORR_ID, LOG_KEY, category);

    // Rollback the transaction
    transactionManager.rollback(status);

    // Verify record was rolled back in PostgreSQL
    setAuthenticatedUser(txUserId, "tx@test.com", "USER");
    Optional<Category> lookup = categoryRepository.findByName(CORR_ID, LOG_KEY, tempCategoryName);
    assertTrue(lookup.isEmpty(), "Category inserted inside rolled back transaction must not exist in DB");
  }

  @Test
  @DisplayName("6. Concurrent password reset token consumption atomic test")
  void testConcurrentPasswordResetTokenConsumption() throws Exception {
    String testEmail = "concurrency_" + System.currentTimeMillis() + "@test.com";
    String tokenHash = "sha256_concurrent_token_" + System.currentTimeMillis();

    PasswordResetToken token =
        PasswordResetToken.builder()
            .tokenHash(tokenHash)
            .email(testEmail)
            .expiresAt(Instant.now().plusSeconds(600))
            .used(false)
            .build();

    passwordResetTokenRepository.save(token);

    int threadCount = 8;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        try {
          startLatch.await();
          // Direct atomic conditional update: UPDATE password_reset_tokens SET used = TRUE WHERE token_hash = :hash AND used = FALSE
          int updated =
              jdbcTemplate.update(
                  "UPDATE password_reset_tokens SET used = TRUE WHERE token_hash = ? AND used = FALSE",
                  tokenHash);
          if (updated == 1) {
            successCount.incrementAndGet();
          }
        } catch (Exception e) {
          // ignore
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    assertEquals(
        1,
        successCount.get(),
        "Exactly ONE concurrent thread must succeed in consuming the password reset token atomically");
  }

  @Test
  @DisplayName("7. Client-provided user ID cannot override authenticated principal")
  void testClientProvidedUserIdCannotOverrideAuthenticatedUser() throws Exception {
    long legitimateUser = createTestUser("legit_" + System.currentTimeMillis() + "@test.com", "Legit User");
    long victimUser = createTestUser("victim_" + System.currentTimeMillis() + "@test.com", "Victim User");

    // Authenticate as legitimateUser
    setAuthenticatedUser(legitimateUser, "legit@test.com", "USER");

    // Attempt to inject victimUser id into category creation
    Category spoofedCategory = Category.builder()
        .userId(victimUser) // Maliciously spoofed user ID
        .name("Spoofed Category " + System.currentTimeMillis())
        .icon("folder")
        .status(1)
        .build();

    long createdCatId = categoryRepository.save(CORR_ID, LOG_KEY, spoofedCategory);
    assertTrue(createdCatId > 0);

    // Verify in database: user_id MUST be legitimateUser, NOT victimUser
    Optional<CategoryDto> readByLegit = categoryRepository.findById(CORR_ID, LOG_KEY, createdCatId);
    assertTrue(readByLegit.isPresent(), "Category must be owned by the authenticated legitimate user");

    // Switch context to victimUser: MUST NOT be visible
    setAuthenticatedUser(victimUser, "victim@test.com", "USER");
    Optional<CategoryDto> readByVictim = categoryRepository.findById(CORR_ID, LOG_KEY, createdCatId);
    assertTrue(readByVictim.isEmpty(), "Victim user MUST NOT see records created by another user who spoofed their ID");
  }

  @Test
  @DisplayName("8. Cross-user entity reference isolation - cannot assign another user category to product")
  void testCrossUserEntityReferenceIsolation() throws Exception {
    long userA = createTestUser("usera_cat_" + System.currentTimeMillis() + "@test.com", "User A");
    long userB = createTestUser("userb_prod_" + System.currentTimeMillis() + "@test.com", "User B");

    // User A creates a category
    setAuthenticatedUser(userA, "usera@test.com", "USER");
    Category userACategory = Category.builder()
        .name("User A Dept " + System.currentTimeMillis())
        .icon("gear")
        .status(1)
        .build();
    long catAId = categoryRepository.save(CORR_ID, LOG_KEY, userACategory);

    // User B tries to look up User A category by name -> MUST be empty
    setAuthenticatedUser(userB, "userb@test.com", "USER");
    Optional<Category> lookupAttempt = categoryRepository.findByName(CORR_ID, LOG_KEY, userACategory.getName());
    assertTrue(lookupAttempt.isEmpty(), "User B MUST NOT be able to resolve User A category by name");

    // User B tries to look up User A category by ID -> MUST be empty
    Optional<CategoryDto> lookupById = categoryRepository.findById(CORR_ID, LOG_KEY, catAId);
    assertTrue(lookupById.isEmpty(), "User B MUST NOT be able to resolve User A category by ID");
  }

  @Test
  @DisplayName("9. Core Inventory Domain: Stock-In increases balance and records ledger")
  void testCoreInventoryDomain_StockIn() throws Exception {
    long userId = createTestUser("stockin_" + System.currentTimeMillis() + "@test.com", "StockIn User");
    setAuthenticatedUser(userId, "stockin@test.com", "USER");

    long catId = categoryRepository.save(CORR_ID, LOG_KEY, Category.builder().name("Cat_" + System.currentTimeMillis()).icon("box").status(1).build());

    Product product = Product.builder()
        .userId(userId)
        .name("Item StockIn " + System.currentTimeMillis())
        .sku("SKU-IN-" + System.currentTimeMillis())
        .categoryId(catId)
        .qty(10)
        .reorderPoint(5)
        .build();
    long prodId = productRepository.save(CORR_ID, LOG_KEY, product);

    com.stockiq.inventory.dto.StockDto moveDto = com.stockiq.inventory.dto.StockDto.builder()
        .productId(prodId)
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(15)
        .reasonReference("PO-98765")
        .build();

    com.stockiq.inventory.dto.StockDto result = stockService.logMovement(CORR_ID, LOG_KEY, moveDto, String.valueOf(userId));

    assertEquals(10, result.getPreviousQty());
    assertEquals(25, result.getResultingQty());

    // Verify product balance in DB
    ProductDto updatedProd = productRepository.findById(CORR_ID, LOG_KEY, prodId).orElseThrow();
    assertEquals(25, updatedProd.getQty());

    // Verify ledger entry
    List<com.stockiq.inventory.dto.StockDto> ledger = stockRepository.findByProductId(CORR_ID, LOG_KEY, prodId);
    assertFalse(ledger.isEmpty());
    assertEquals(10, ledger.get(0).getPreviousQty());
    assertEquals(25, ledger.get(0).getResultingQty());
    assertEquals("PO-98765", ledger.get(0).getReasonReference());
  }

  @Test
  @DisplayName("10. Core Inventory Domain: Stock-Out decreases balance and records ledger")
  void testCoreInventoryDomain_StockOut() throws Exception {
    long userId = createTestUser("stockout_" + System.currentTimeMillis() + "@test.com", "StockOut User");
    setAuthenticatedUser(userId, "stockout@test.com", "USER");

    long catId = categoryRepository.save(CORR_ID, LOG_KEY, Category.builder().name("Cat_" + System.currentTimeMillis()).icon("box").status(1).build());

    Product product = Product.builder()
        .userId(userId)
        .name("Item StockOut " + System.currentTimeMillis())
        .sku("SKU-OUT-" + System.currentTimeMillis())
        .categoryId(catId)
        .qty(30)
        .reorderPoint(5)
        .build();
    long prodId = productRepository.save(CORR_ID, LOG_KEY, product);

    com.stockiq.inventory.dto.StockDto moveDto = com.stockiq.inventory.dto.StockDto.builder()
        .productId(prodId)
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_OUT)
        .qty(12)
        .reasonReference("SO-12345")
        .build();

    com.stockiq.inventory.dto.StockDto result = stockService.logMovement(CORR_ID, LOG_KEY, moveDto, String.valueOf(userId));

    assertEquals(30, result.getPreviousQty());
    assertEquals(18, result.getResultingQty());

    // Verify product balance in DB
    ProductDto updatedProd = productRepository.findById(CORR_ID, LOG_KEY, prodId).orElseThrow();
    assertEquals(18, updatedProd.getQty());
  }

  @Test
  @DisplayName("11. Core Inventory Domain: Stock-Out beyond available balance is rejected with 400 and ledger untouched")
  void testCoreInventoryDomain_StockOutBeyondAvailable() throws Exception {
    long userId = createTestUser("stockrej_" + System.currentTimeMillis() + "@test.com", "StockRej User");
    setAuthenticatedUser(userId, "stockrej@test.com", "USER");

    long catId = categoryRepository.save(CORR_ID, LOG_KEY, Category.builder().name("Cat_" + System.currentTimeMillis()).icon("box").status(1).build());

    Product product = Product.builder()
        .userId(userId)
        .name("Item Reject " + System.currentTimeMillis())
        .sku("SKU-REJ-" + System.currentTimeMillis())
        .categoryId(catId)
        .qty(5)
        .reorderPoint(2)
        .build();
    long prodId = productRepository.save(CORR_ID, LOG_KEY, product);

    com.stockiq.inventory.dto.StockDto moveDto = com.stockiq.inventory.dto.StockDto.builder()
        .productId(prodId)
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_OUT)
        .qty(10) // exceeds available qty of 5
        .reasonReference("SO-EXCESS")
        .build();

    assertThrows(
        com.stockiq.inventory.common.exception.GeneralStatusCodeErrorException.class,
        () -> stockService.logMovement(CORR_ID, LOG_KEY, moveDto, String.valueOf(userId)));

    // Verify quantity unchanged in DB
    ProductDto prodAfter = productRepository.findById(CORR_ID, LOG_KEY, prodId).orElseThrow();
    assertEquals(5, prodAfter.getQty(), "Product quantity must remain unchanged after rejected stock-out");

    // Verify no ledger entries written
    List<com.stockiq.inventory.dto.StockDto> ledger = stockRepository.findByProductId(CORR_ID, LOG_KEY, prodId);
    assertTrue(ledger.isEmpty(), "Ledger must have no entries for rejected stock movement");
  }

  @Test
  @DisplayName("12. Core Inventory Domain: Adjustment updates balance to target and writes ledger")
  void testCoreInventoryDomain_Adjustment() throws Exception {
    long userId = createTestUser("stockadj_" + System.currentTimeMillis() + "@test.com", "StockAdj User");
    setAuthenticatedUser(userId, "stockadj@test.com", "USER");

    long catId = categoryRepository.save(CORR_ID, LOG_KEY, Category.builder().name("Cat_" + System.currentTimeMillis()).icon("box").status(1).build());

    Product product = Product.builder()
        .userId(userId)
        .name("Item Adj " + System.currentTimeMillis())
        .sku("SKU-ADJ-" + System.currentTimeMillis())
        .categoryId(catId)
        .qty(50)
        .reorderPoint(10)
        .build();
    long prodId = productRepository.save(CORR_ID, LOG_KEY, product);

    com.stockiq.inventory.dto.StockDto moveDto = com.stockiq.inventory.dto.StockDto.builder()
        .productId(prodId)
        .type(com.stockiq.inventory.common.enums.stock.MovementType.ADJUSTMENT)
        .qty(42) // Target balance set to 42
        .reasonReference("ANNUAL-PHYSICAL-AUDIT")
        .build();

    com.stockiq.inventory.dto.StockDto result = stockService.logMovement(CORR_ID, LOG_KEY, moveDto, String.valueOf(userId));

    assertEquals(50, result.getPreviousQty());
    assertEquals(42, result.getResultingQty());

    ProductDto prodAfter = productRepository.findById(CORR_ID, LOG_KEY, prodId).orElseThrow();
    assertEquals(42, prodAfter.getQty());
  }

  @Test
  @DisplayName("13. Core Inventory Domain: Pessimistic Row Locking Concurrency Test under High Contention")
  void testCoreInventoryDomain_PessimisticLockingConcurrency() throws Exception {
    long userId = createTestUser("concurr_" + System.currentTimeMillis() + "@test.com", "Concurrency User");
    setAuthenticatedUser(userId, "concurr@test.com", "USER");

    long catId = categoryRepository.save(CORR_ID, LOG_KEY, Category.builder().name("Cat_" + System.currentTimeMillis()).icon("box").status(1).build());

    Product product = Product.builder()
        .userId(userId)
        .name("Item Concurrency " + System.currentTimeMillis())
        .sku("SKU-CONC-" + System.currentTimeMillis())
        .categoryId(catId)
        .qty(100)
        .reorderPoint(10)
        .build();
    long prodId = productRepository.save(CORR_ID, LOG_KEY, product);

    int threadCount = 10;
    int stockOutPerThread = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      final int idx = i;
      executor.submit(() -> {
        try {
          startLatch.await();
          setAuthenticatedUser(userId, "concurr@test.com", "USER");

          com.stockiq.inventory.dto.StockDto moveDto = com.stockiq.inventory.dto.StockDto.builder()
              .productId(prodId)
              .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_OUT)
              .qty(stockOutPerThread)
              .reasonReference("CONCURRENT-ORDER-" + idx)
              .build();

          stockService.logMovement(CORR_ID, LOG_KEY, moveDto, String.valueOf(userId));
          successCount.incrementAndGet();
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    assertEquals(threadCount, successCount.get(), "All 10 concurrent stock out operations must succeed");

    // Authenticate again to query
    setAuthenticatedUser(userId, "concurr@test.com", "USER");
    ProductDto finalProd = productRepository.findById(CORR_ID, LOG_KEY, prodId).orElseThrow();

    // Initial 100 - (10 threads * 5 units) = exactly 50 units (zero lost updates)
    assertEquals(50, finalProd.getQty(), "Final quantity must be exactly 50 with no lost updates under concurrency");

    List<com.stockiq.inventory.dto.StockDto> ledger = stockRepository.findByProductId(CORR_ID, LOG_KEY, prodId);
    assertEquals(10, ledger.size(), "Ledger must contain exactly 10 audit records");
  }

  @Test
  @DisplayName("14. Core Inventory Domain: SKU uniqueness per user enforced in PostgreSQL")
  void testCoreInventoryDomain_SkuUniquenessPerUser() throws Exception {
    long userId = createTestUser("sku_uniq_" + System.currentTimeMillis() + "@test.com", "SKU User");
    setAuthenticatedUser(userId, "sku@test.com", "USER");

    long catId = categoryRepository.save(CORR_ID, LOG_KEY, Category.builder().name("Cat_" + System.currentTimeMillis()).icon("box").status(1).build());

    String sharedSku = "SKU-SHARED-" + System.currentTimeMillis();

    Product product1 = Product.builder()
        .userId(userId)
        .name("Item 1 " + System.currentTimeMillis())
        .sku(sharedSku)
        .categoryId(catId)
        .qty(10)
        .reorderPoint(5)
        .build();
    long id1 = productRepository.save(CORR_ID, LOG_KEY, product1);
    assertTrue(id1 > 0);

    // Duplicate SKU for the SAME user must fail with DuplicateKeyException / DataIntegrityViolationException
    Product product2 = Product.builder()
        .userId(userId)
        .name("Item 2 " + System.currentTimeMillis())
        .sku(sharedSku)
        .categoryId(catId)
        .qty(10)
        .reorderPoint(5)
        .build();

    assertThrows(
        Exception.class,
        () -> productRepository.save(CORR_ID, LOG_KEY, product2),
        "PostgreSQL must reject duplicate SKU for the same user");

    // But another user CAN use the same SKU
    long otherUser = createTestUser("sku_other_" + System.currentTimeMillis() + "@test.com", "Other User");
    setAuthenticatedUser(otherUser, "sku_other@test.com", "USER");

    long otherCatId = categoryRepository.save(CORR_ID, LOG_KEY, Category.builder().name("CatOther_" + System.currentTimeMillis()).icon("box").status(1).build());

    Product productOther = Product.builder()
        .userId(otherUser)
        .name("Item Other " + System.currentTimeMillis())
        .sku(sharedSku)
        .categoryId(otherCatId)
        .qty(15)
        .reorderPoint(5)
        .build();
    long otherProdId = productRepository.save(CORR_ID, LOG_KEY, productOther);
    assertTrue(otherProdId > 0, "Different user is permitted to use the same SKU");
  }


  @Test
  @DisplayName("15. Multi-Tenant Supplier Isolation: Product cannot reference another user's supplier")
  void testProductCannotReferenceAnotherUserSupplier() throws Exception {
    long userA = createTestUser("usera_sup_" + System.currentTimeMillis() + "@test.com", "User A");
    long userB = createTestUser("userb_prod_" + System.currentTimeMillis() + "@test.com", "User B");

    // User A creates a supplier
    setAuthenticatedUser(userA, "usera_sup@test.com", "USER");
    Supplier supplierA = Supplier.builder()
        .userId(userA)
        .name("User A Exclusive Supplier " + System.currentTimeMillis())
        .category("Hardware")
        .status("Active")
        .email("suppliera@test.com")
        .phone("111-222-333")
        .build();
    long supplierAId = supplierRepository.save(CORR_ID, LOG_KEY, supplierA);
    assertTrue(supplierAId > 0);

    // User B attempts to create a product referencing User A's supplier
    setAuthenticatedUser(userB, "userb_prod@test.com", "USER");
    long catBId = categoryRepository.save(CORR_ID, LOG_KEY, Category.builder().name("CatB_" + System.currentTimeMillis()).icon("box").status(1).build());

    // 1) Repository/Database-level rejection via composite foreign key (supplier_id, user_id)
    Product crossUserProduct = Product.builder()
        .userId(userB)
        .name("Cross-Supplier Item " + System.currentTimeMillis())
        .sku("SKU-CROSS-" + System.currentTimeMillis())
        .categoryId(catBId)
        .supplierId(supplierAId) // user A's supplier!
        .qty(10)
        .reorderPoint(5)
        .build();

    assertThrows(
        Exception.class,
        () -> productRepository.save(CORR_ID, LOG_KEY, crossUserProduct),
        "PostgreSQL composite foreign key fk_products_supplier_user must reject cross-tenant supplier assignment");
  }

  @Test
  @DisplayName("16. Immutable Inventory Ledger: Updates and Deletions on stock_transactions are strictly forbidden")
  void testImmutableInventoryLedger_UpdateAndDeleteProhibited() throws Exception {
    long userId = createTestUser("immutable_" + System.currentTimeMillis() + "@test.com", "Immutable User");
    setAuthenticatedUser(userId, "immutable@test.com", "USER");

    long catId = categoryRepository.save(CORR_ID, LOG_KEY, Category.builder().name("Cat_" + System.currentTimeMillis()).icon("box").status(1).build());

    Product product = Product.builder()
        .userId(userId)
        .name("Item Ledger Immutable " + System.currentTimeMillis())
        .sku("SKU-IMM-" + System.currentTimeMillis())
        .categoryId(catId)
        .qty(10)
        .reorderPoint(5)
        .build();
    long prodId = productRepository.save(CORR_ID, LOG_KEY, product);

    com.stockiq.inventory.dto.StockDto moveDto = com.stockiq.inventory.dto.StockDto.builder()
        .productId(prodId)
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(20)
        .reasonReference("PO-ORIGINAL")
        .build();
    com.stockiq.inventory.dto.StockDto res = stockService.logMovement(CORR_ID, LOG_KEY, moveDto, String.valueOf(userId));
    long txId = res.getId();
    assertTrue(txId > 0);

    // Attempt direct SQL UPDATE on stock_transactions -> Trigger must raise exception
    JdbcTemplate directJdbc = new JdbcTemplate(dataSource);
    assertThrows(
        Exception.class,
        () -> directJdbc.update("UPDATE stock_transactions SET qty = 999 WHERE id = ?", txId),
        "PostgreSQL trigger must abort any direct UPDATE to stock_transactions");

    // Attempt direct SQL DELETE on stock_transactions -> Trigger must raise exception
    assertThrows(
        Exception.class,
        () -> directJdbc.update("DELETE FROM stock_transactions WHERE id = ?", txId),
        "PostgreSQL trigger must abort any direct DELETE to stock_transactions");
  }

  @Test
  @DisplayName("17. Opening Stock Policy: Positive initial stock automatically records opening transaction")
  void testOpeningStockPolicy_GeneratesAuditableLedgerEntry() throws Exception {
    long userId = createTestUser("opening_" + System.currentTimeMillis() + "@test.com", "Opening User");
    setAuthenticatedUser(userId, "opening@test.com", "USER");

    Category cat = Category.builder().name("CatOpening_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto createDto = ProductDto.builder()
        .name("Product With Opening Stock " + System.currentTimeMillis())
        .sku("SKU-OPEN-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(50) // positive initial opening stock
        .reorder(10)
        .build();

    ProductDto created = productService.save(CORR_ID, LOG_KEY, createDto);
    assertNotNull(created.getId());

    // Verify product quantity
    ProductDto inDb = productRepository.findById(CORR_ID, LOG_KEY, created.getId()).orElseThrow();
    assertEquals(50, inDb.getQty());

    // Verify opening stock transaction in ledger
    List<com.stockiq.inventory.dto.StockDto> history = stockRepository.findByProductId(CORR_ID, LOG_KEY, created.getId());
    assertEquals(1, history.size(), "Opening stock must produce an opening transaction in ledger");
    assertEquals(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN, history.get(0).getType());
    assertEquals(0, history.get(0).getPreviousQty());
    assertEquals(50, history.get(0).getResultingQty());
    assertEquals(50, history.get(0).getDeltaQty());
    assertTrue(history.get(0).getReasonReference().contains("OPENING-STOCK"));
  }

  @Test
  @DisplayName("18. Movement Type Normalization: Rejects unsupported movement types and handles legacy IN/OUT reading")
  void testMovementTypeNormalization() {
    // Normalization mapping
    assertEquals(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN, com.stockiq.inventory.common.enums.stock.MovementType.fromString("IN"));
    assertEquals(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN, com.stockiq.inventory.common.enums.stock.MovementType.fromString("STOCK_IN"));
    assertEquals(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_OUT, com.stockiq.inventory.common.enums.stock.MovementType.fromString("OUT"));
    assertEquals(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_OUT, com.stockiq.inventory.common.enums.stock.MovementType.fromString("STOCK_OUT"));
    assertEquals(com.stockiq.inventory.common.enums.stock.MovementType.ADJUSTMENT, com.stockiq.inventory.common.enums.stock.MovementType.fromString("ADJUSTMENT"));

    // Rejection of invalid types
    assertThrows(IllegalArgumentException.class, () -> com.stockiq.inventory.common.enums.stock.MovementType.fromString("INVALID_TRANSFER"));
    assertThrows(IllegalArgumentException.class, () -> com.stockiq.inventory.common.enums.stock.MovementType.fromString("CORRECTION"));
  }


  // =========================================================================
  // Phase 3.2: Multi-Warehouse Inventory Architecture Integration Tests
  // =========================================================================

  @Test
  @DisplayName("19. Warehouse Creation & Tenant Isolation: Cross-tenant warehouse lookup returns empty / forbidden")
  void testWarehouseCreation_AndTenantIsolation() throws Exception {
    long userA = createTestUser("wh_iso_a_" + System.currentTimeMillis() + "@test.com", "User A");
    long userB = createTestUser("wh_iso_b_" + System.currentTimeMillis() + "@test.com", "User B");

    setAuthenticatedUser(userA, "wh_iso_a@test.com", "USER");
    com.stockiq.inventory.dto.WarehouseDto whA = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-NORTH-" + System.currentTimeMillis())
        .name("North Hub")
        .build());
    assertNotNull(whA.getId());

    // User A can access
    com.stockiq.inventory.dto.WarehouseDto fetchedA = warehouseService.findById(CORR_ID, LOG_KEY, whA.getId());
    assertEquals("North Hub", fetchedA.getName());

    // Switch to User B - User B cannot find User A's warehouse
    setAuthenticatedUser(userB, "wh_iso_b@test.com", "USER");
    assertThrows(Exception.class, () -> warehouseService.findById(CORR_ID, LOG_KEY, whA.getId()),
        "User B must not be able to access User A warehouse");
  }

  @Test
  @DisplayName("20. Duplicate Warehouse Code Rejection: Unique constraint per tenant enforces unique warehouse code")
  void testDuplicateWarehouseCodeRejection() throws Exception {
    long userId = createTestUser("wh_dup_" + System.currentTimeMillis() + "@test.com", "Wh Dup User");
    setAuthenticatedUser(userId, "wh_dup@test.com", "USER");

    String code = "WH-DUP-" + System.currentTimeMillis();
    warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder().code(code).name("Hub 1").build());

    // Attempting to create duplicate warehouse code for same user must fail
    assertThrows(Exception.class, () ->
        warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder().code(code).name("Hub 2").build()),
        "Duplicate warehouse code for the same tenant must be rejected");
  }

  @Test
  @DisplayName("21. Warehouse-Specific Inventory Balances: Dedicated user_id + warehouse_id + product_id + qty model")
  void testWarehouseSpecificInventoryBalances() throws Exception {
    long userId = createTestUser("wh_bal_" + System.currentTimeMillis() + "@test.com", "Wh Balance User");
    setAuthenticatedUser(userId, "wh_bal@test.com", "USER");

    Category cat = Category.builder().name("CatBal_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prod = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Product Bal " + System.currentTimeMillis())
        .sku("SKU-BAL-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(0)
        .build());

    com.stockiq.inventory.dto.WarehouseDto wh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-BAL-" + System.currentTimeMillis())
        .name("Balance Hub")
        .build());

    // Add stock directly to this warehouse
    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(wh.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(75)
        .build(), String.valueOf(userId));

    // Verify balance in warehouse_inventory
    var inv = warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, wh.getId(), prod.getId());
    assertTrue(inv.isPresent());
    assertEquals(75, inv.get().getQty());
    assertEquals(75, warehouseInventoryRepository.getAggregateProductStock(CORR_ID, LOG_KEY, prod.getId()));
  }

  @Test
  @DisplayName("22. Same Product Stocked in Two Warehouses Independently")
  void testSameProductStockedInTwoWarehousesIndependently() throws Exception {
    long userId = createTestUser("wh_two_" + System.currentTimeMillis() + "@test.com", "Two WH User");
    setAuthenticatedUser(userId, "wh_two@test.com", "USER");

    Category cat = Category.builder().name("CatTwo_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prod = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Two WH Product " + System.currentTimeMillis())
        .sku("SKU-TWO-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(0)
        .build());

    com.stockiq.inventory.dto.WarehouseDto wh1 = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-1-" + System.currentTimeMillis())
        .name("Warehouse One")
        .build());

    com.stockiq.inventory.dto.WarehouseDto wh2 = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-2-" + System.currentTimeMillis())
        .name("Warehouse Two")
        .build());

    // Stock in 40 to WH1
    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(wh1.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(40)
        .build(), String.valueOf(userId));

    // Stock in 60 to WH2
    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(wh2.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(60)
        .build(), String.valueOf(userId));

    // Verify independent warehouse balances
    assertEquals(40, warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, wh1.getId(), prod.getId()).orElseThrow().getQty());
    assertEquals(60, warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, wh2.getId(), prod.getId()).orElseThrow().getQty());

    // Verify aggregate product stock rollup
    assertEquals(100, warehouseInventoryRepository.getAggregateProductStock(CORR_ID, LOG_KEY, prod.getId()));
    assertEquals(100, productRepository.findById(CORR_ID, LOG_KEY, prod.getId()).orElseThrow().getQty());
  }

  @Test
  @DisplayName("23. Stock-In and Stock-Out at Selected Warehouse")
  void testStockInAndStockOutAtSelectedWarehouse() throws Exception {
    long userId = createTestUser("wh_inout_" + System.currentTimeMillis() + "@test.com", "Wh InOut User");
    setAuthenticatedUser(userId, "wh_inout@test.com", "USER");

    Category cat = Category.builder().name("CatInOut_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prod = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("InOut Product " + System.currentTimeMillis())
        .sku("SKU-INOUT-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(0)
        .build());

    com.stockiq.inventory.dto.WarehouseDto wh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-IO-" + System.currentTimeMillis())
        .name("IO Warehouse")
        .build());

    // Stock in 100
    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(wh.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(100)
        .build(), String.valueOf(userId));

    // Stock out 35
    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(wh.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_OUT)
        .qty(35)
        .build(), String.valueOf(userId));

    // Assert resulting quantity is 65
    assertEquals(65, warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, wh.getId(), prod.getId()).orElseThrow().getQty());
  }

  @Test
  @DisplayName("24. Insufficient Stock Rejection at Selected Warehouse")
  void testInsufficientStockRejectionAtWarehouse() throws Exception {
    long userId = createTestUser("wh_insuff_" + System.currentTimeMillis() + "@test.com", "Wh Insuff User");
    setAuthenticatedUser(userId, "wh_insuff@test.com", "USER");

    Category cat = Category.builder().name("CatInsuff_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prod = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Insuff Product " + System.currentTimeMillis())
        .sku("SKU-INSUF-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(0)
        .build());

    com.stockiq.inventory.dto.WarehouseDto wh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-INSUF-" + System.currentTimeMillis())
        .name("Insuff Warehouse")
        .build());

    // Stock in 10
    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(wh.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(10)
        .build(), String.valueOf(userId));

    // Attempting to stock out 25 must fail with insufficient stock error
    assertThrows(Exception.class, () ->
        stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
            .productId(prod.getId())
            .warehouseId(wh.getId())
            .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_OUT)
            .qty(25)
            .build(), String.valueOf(userId)));

    // Balance remains 10
    assertEquals(10, warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, wh.getId(), prod.getId()).orElseThrow().getQty());
  }

  @Test
  @DisplayName("25. Successful Inter-Warehouse Transfer: Atomically decrements source and increments destination")
  void testSuccessfulInterWarehouseTransfer() throws Exception {
    long userId = createTestUser("wh_trans_succ_" + System.currentTimeMillis() + "@test.com", "Transfer Success User");
    setAuthenticatedUser(userId, "wh_trans_succ@test.com", "USER");

    Category cat = Category.builder().name("CatTransSucc_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prod = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Transfer Product " + System.currentTimeMillis())
        .sku("SKU-TRANS-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(0)
        .build());

    com.stockiq.inventory.dto.WarehouseDto srcWh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-SRC-" + System.currentTimeMillis())
        .name("Source Hub")
        .build());

    com.stockiq.inventory.dto.WarehouseDto destWh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-DST-" + System.currentTimeMillis())
        .name("Dest Hub")
        .build());

    // Put 100 in source
    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(srcWh.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(100)
        .build(), String.valueOf(userId));

    // Initiate transfer of 40 from src to dest
    var initiated = warehouseTransferService.initiateTransfer(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseTransferDto.builder()
        .sourceWarehouseId(srcWh.getId())
        .destWarehouseId(destWh.getId())
        .productId(prod.getId())
        .qty(40)
        .referenceNote("Rebalance regional stock")
        .build());

    assertEquals("INITIATED", initiated.getStatus());

    // Complete transfer
    var completed = warehouseTransferService.completeTransfer(CORR_ID, LOG_KEY, initiated.getId());
    assertEquals("COMPLETED", completed.getStatus());

    // Verify balances: source = 60, dest = 40
    assertEquals(60, warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, srcWh.getId(), prod.getId()).orElseThrow().getQty());
    assertEquals(40, warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, destWh.getId(), prod.getId()).orElseThrow().getQty());

    // Total aggregate remains 100
    assertEquals(100, warehouseInventoryRepository.getAggregateProductStock(CORR_ID, LOG_KEY, prod.getId()));
  }

  @Test
  @DisplayName("26. Transfer Rollback on Failure: Failed transfer leaves balances intact")
  void testTransferRollbackOnFailure() throws Exception {
    long userId = createTestUser("wh_trans_fail_" + System.currentTimeMillis() + "@test.com", "Transfer Fail User");
    setAuthenticatedUser(userId, "wh_trans_fail@test.com", "USER");

    Category cat = Category.builder().name("CatTransFail_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prod = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Fail Transfer Product " + System.currentTimeMillis())
        .sku("SKU-FAIL-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(0)
        .build());

    com.stockiq.inventory.dto.WarehouseDto srcWh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-SRC-F-" + System.currentTimeMillis())
        .name("Source Hub")
        .build());

    com.stockiq.inventory.dto.WarehouseDto destWh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-DST-F-" + System.currentTimeMillis())
        .name("Dest Hub")
        .build());

    // Source has 20
    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(srcWh.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(20)
        .build(), String.valueOf(userId));

    // Attempt to initiate transfer of 50 (exceeds balance)
    assertThrows(Exception.class, () ->
        warehouseTransferService.initiateTransfer(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseTransferDto.builder()
            .sourceWarehouseId(srcWh.getId())
            .destWarehouseId(destWh.getId())
            .productId(prod.getId())
            .qty(50)
            .build()));

    // Balances unaffected
    assertEquals(20, warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, srcWh.getId(), prod.getId()).orElseThrow().getQty());
  }

  @Test
  @DisplayName("27. Concurrent Transfers: Pessimistic locking prevents lost updates or negative balances")
  void testConcurrentTransfersWithoutLostUpdates() throws Exception {
    long userId = createTestUser("wh_conc_" + System.currentTimeMillis() + "@test.com", "Concurrent Transfer User");
    setAuthenticatedUser(userId, "wh_conc@test.com", "USER");

    Category cat = Category.builder().name("CatConc_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prod = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Conc Product " + System.currentTimeMillis())
        .sku("SKU-CONC-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(0)
        .build());

    com.stockiq.inventory.dto.WarehouseDto srcWh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-SRC-C-" + System.currentTimeMillis())
        .name("Source Hub")
        .build());

    com.stockiq.inventory.dto.WarehouseDto destWh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-DST-C-" + System.currentTimeMillis())
        .name("Dest Hub")
        .build());

    // Deposit 100 in source
    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(srcWh.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(100)
        .build(), String.valueOf(userId));

    // Create 10 transfers of 10 items each
    long[] transferIds = new long[10];
    for (int i = 0; i < 10; i++) {
      var initiated = warehouseTransferService.initiateTransfer(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseTransferDto.builder()
          .sourceWarehouseId(srcWh.getId())
          .destWarehouseId(destWh.getId())
          .productId(prod.getId())
          .qty(10)
          .build());
      transferIds[i] = initiated.getId();
    }

    // Execute completion concurrently
    int threads = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threads);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threads; i++) {
      final long tId = transferIds[i];
      executor.submit(() -> {
        try {
          startLatch.await();
          setAuthenticatedUser(userId, "wh_conc@test.com", "USER");
          warehouseTransferService.completeTransfer(CORR_ID, LOG_KEY, tId);
          successCount.incrementAndGet();
        } catch (Exception ignored) {
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    // Verify all 10 transfers succeeded and exact balances match
    assertEquals(10, successCount.get());
    setAuthenticatedUser(userId, "wh_conc@test.com", "USER");
    assertEquals(0, warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, srcWh.getId(), prod.getId()).orElseThrow().getQty());
    assertEquals(100, warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, destWh.getId(), prod.getId()).orElseThrow().getQty());
  }

  @Test
  @DisplayName("28. Duplicate Transfer Completion Rejection: Prevents replay of already completed transfer")
  void testDuplicateTransferCompletionRejection() throws Exception {
    long userId = createTestUser("wh_dup_comp_" + System.currentTimeMillis() + "@test.com", "Dup Comp User");
    setAuthenticatedUser(userId, "wh_dup_comp@test.com", "USER");

    Category cat = Category.builder().name("CatDupComp_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prod = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Dup Comp Product " + System.currentTimeMillis())
        .sku("SKU-DUPCOMP-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(0)
        .build());

    com.stockiq.inventory.dto.WarehouseDto srcWh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-SRC-DC-" + System.currentTimeMillis())
        .name("Source Hub")
        .build());

    com.stockiq.inventory.dto.WarehouseDto destWh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-DST-DC-" + System.currentTimeMillis())
        .name("Dest Hub")
        .build());

    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(srcWh.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(50)
        .build(), String.valueOf(userId));

    var initiated = warehouseTransferService.initiateTransfer(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseTransferDto.builder()
        .sourceWarehouseId(srcWh.getId())
        .destWarehouseId(destWh.getId())
        .productId(prod.getId())
        .qty(20)
        .build());

    // First completion succeeds
    warehouseTransferService.completeTransfer(CORR_ID, LOG_KEY, initiated.getId());

    // Second completion attempt must be rejected
    assertThrows(Exception.class, () ->
        warehouseTransferService.completeTransfer(CORR_ID, LOG_KEY, initiated.getId()),
        "Replay of already completed transfer must be rejected");

    // Balances reflect exactly one completion: source = 30, dest = 20
    assertEquals(30, warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, srcWh.getId(), prod.getId()).orElseThrow().getQty());
    assertEquals(20, warehouseInventoryRepository.findByWarehouseAndProduct(CORR_ID, LOG_KEY, destWh.getId(), prod.getId()).orElseThrow().getQty());
  }

  @Test
  @DisplayName("29. Cross-Tenant Warehouse Transfer Rejection: Cannot transfer to/from another user's warehouse")
  void testCrossTenantWarehouseTransferRejection() throws Exception {
    long userA = createTestUser("wh_cross_a_" + System.currentTimeMillis() + "@test.com", "User A");
    long userB = createTestUser("wh_cross_b_" + System.currentTimeMillis() + "@test.com", "User B");

    setAuthenticatedUser(userA, "wh_cross_a@test.com", "USER");
    Category cat = Category.builder().name("CatCross_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prodA = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Prod Cross A " + System.currentTimeMillis())
        .sku("SKU-CROSS-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(50)
        .build());

    com.stockiq.inventory.dto.WarehouseDto whA = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-CROSS-A-" + System.currentTimeMillis())
        .name("WH A")
        .build());

    // User B creates a warehouse
    setAuthenticatedUser(userB, "wh_cross_b@test.com", "USER");
    com.stockiq.inventory.dto.WarehouseDto whB = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-CROSS-B-" + System.currentTimeMillis())
        .name("WH B")
        .build());

    // User A attempts transfer with User B's warehouse as destination
    setAuthenticatedUser(userA, "wh_cross_a@test.com", "USER");
    assertThrows(Exception.class, () ->
        warehouseTransferService.initiateTransfer(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseTransferDto.builder()
            .sourceWarehouseId(whA.getId())
            .destWarehouseId(whB.getId()) // Belongs to User B!
            .productId(prodA.getId())
            .qty(10)
            .build()),
        "Cross-tenant warehouse transfer must be rejected");
  }

  @Test
  @DisplayName("30. Immutable Ledger Behavior After Transfers: Appends TRANSFER-OUT and TRANSFER-IN immutable ledger records")
  void testImmutableLedgerBehaviorAfterTransfers() throws Exception {
    long userId = createTestUser("wh_ledger_" + System.currentTimeMillis() + "@test.com", "Ledger WH User");
    setAuthenticatedUser(userId, "wh_ledger@test.com", "USER");

    Category cat = Category.builder().name("CatLedgerWH_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prod = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Ledger WH Prod " + System.currentTimeMillis())
        .sku("SKU-LEDGER-WH-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(0)
        .build());

    com.stockiq.inventory.dto.WarehouseDto srcWh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-SRC-L-" + System.currentTimeMillis())
        .name("Source Hub")
        .build());

    com.stockiq.inventory.dto.WarehouseDto destWh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-DST-L-" + System.currentTimeMillis())
        .name("Dest Hub")
        .build());

    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(srcWh.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(100)
        .build(), String.valueOf(userId));

    var initiated = warehouseTransferService.initiateTransfer(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseTransferDto.builder()
        .sourceWarehouseId(srcWh.getId())
        .destWarehouseId(destWh.getId())
        .productId(prod.getId())
        .qty(30)
        .build());

    warehouseTransferService.completeTransfer(CORR_ID, LOG_KEY, initiated.getId());

    // Query ledger history for product
    List<com.stockiq.inventory.dto.StockDto> history = stockRepository.findByProductId(CORR_ID, LOG_KEY, prod.getId());
    // Initial Stock In (100) + Transfer Out (30) + Transfer In (30) = 3 transactions
    assertEquals(3, history.size());

    // Ensure transactions record warehouse context
    boolean foundTransferOut = history.stream().anyMatch(t -> t.getReasonReference() != null && t.getReasonReference().contains("TRANSFER-OUT") && t.getWarehouseId().equals(srcWh.getId()));
    boolean foundTransferIn = history.stream().anyMatch(t -> t.getReasonReference() != null && t.getReasonReference().contains("TRANSFER-IN") && t.getWarehouseId().equals(destWh.getId()));

    assertTrue(foundTransferOut, "Ledger must record TRANSFER-OUT for source warehouse");
    assertTrue(foundTransferIn, "Ledger must record TRANSFER-IN for destination warehouse");

    // Database-level immutability trigger test on transfer ledger rows
    JdbcTemplate directJdbc = new JdbcTemplate(dataSource);
    long lastTxId = history.get(0).getId();
    assertThrows(Exception.class, () ->
        directJdbc.update("UPDATE stock_transactions SET qty = 999 WHERE id = ?", lastTxId),
        "PostgreSQL trigger must abort update on transfer ledger records");
  }

  @Test
  @DisplayName("31. Transfer Lifecycle Hardening: Database status constraint rejects invalid status")
  void testTransferStatusDatabaseConstraint() throws Exception {
    long userId = createTestUser("wh_stat_chk_" + System.currentTimeMillis() + "@test.com", "Status User");
    setAuthenticatedUser(userId, "wh_stat_chk@test.com", "USER");

    Category cat = Category.builder().name("CatStatChk_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prod = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Prod Stat Chk " + System.currentTimeMillis())
        .sku("SKU-STAT-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(10)
        .build());

    var wh1 = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-S1-" + System.currentTimeMillis()).name("WH 1").build());
    var wh2 = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-S2-" + System.currentTimeMillis()).name("WH 2").build());

    JdbcTemplate directJdbc = new JdbcTemplate(dataSource);

    // 1. Direct insert with INVALID status should fail DB constraint
    assertThrows(Exception.class, () -> {
      directJdbc.update(
          "INSERT INTO warehouse_transfers (user_id, source_warehouse_id, dest_warehouse_id, product_id, qty, status, initiating_user) "
              + "VALUES (?, ?, ?, ?, ?, 'INVALID_STATUS', 'tester')",
          userId, wh1.getId(), wh2.getId(), prod.getId(), 5);
    }, "Database check constraint chk_transfers_status must reject invalid status");

    // 2. Direct inserts with all 4 valid statuses must succeed
    String[] validStatuses = {"INITIATED", "APPROVED", "COMPLETED", "CANCELLED"};
    for (String validStatus : validStatuses) {
      int inserted = directJdbc.update(
          "INSERT INTO warehouse_transfers (user_id, source_warehouse_id, dest_warehouse_id, product_id, qty, status, initiating_user) "
              + "VALUES (?, ?, ?, ?, ?, ?, 'tester')",
          userId, wh1.getId(), wh2.getId(), prod.getId(), 5, validStatus);
      assertEquals(1, inserted, "Status " + validStatus + " must be accepted by database");
    }
  }

  @Test
  @DisplayName("32. Transfer Lifecycle Hardening: Full Approval, Timestamp, and Replay Protection Lifecycle")
  void testTransferApprovalLifecycleAndTimestamps() throws Exception {
    long userId = createTestUser("wh_appr_" + System.currentTimeMillis() + "@test.com", "Approve User");
    setAuthenticatedUser(userId, "wh_appr@test.com", "USER");

    Category cat = Category.builder().name("CatAppr_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prod = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Prod Appr " + System.currentTimeMillis())
        .sku("SKU-APPR-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(0)
        .build());

    var whA = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-AP-A-" + System.currentTimeMillis()).name("WH A").build());
    var whB = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-AP-B-" + System.currentTimeMillis()).name("WH B").build());

    // Deposit 100 in WH A
    stockService.logMovement(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.StockDto.builder()
        .productId(prod.getId())
        .warehouseId(whA.getId())
        .type(com.stockiq.inventory.common.enums.stock.MovementType.STOCK_IN)
        .qty(100)
        .build(), String.valueOf(userId));

    // Case 1: INITIATED -> APPROVED -> COMPLETED
    var t1 = warehouseTransferService.initiateTransfer(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseTransferDto.builder()
        .sourceWarehouseId(whA.getId())
        .destWarehouseId(whB.getId())
        .productId(prod.getId())
        .qty(25)
        .referenceNote("Approval flow test")
        .build());

    assertEquals("INITIATED", t1.getStatus());
    assertNull(t1.getApprovedAt());
    assertNull(t1.getCompletedAt());
    assertNull(t1.getCancelledAt());

    // Approve transfer
    var approvedT1 = warehouseTransferService.approveTransfer(CORR_ID, LOG_KEY, t1.getId());
    assertEquals("APPROVED", approvedT1.getStatus());
    assertNotNull(approvedT1.getApprovedAt(), "approved_at must be populated");
    assertEquals(String.valueOf(userId), approvedT1.getApprovingUser());
    assertNull(approvedT1.getCompletedAt(), "completed_at must not be populated upon approval");
    assertNull(approvedT1.getCancelledAt());

    // Repeated approval must be rejected
    assertThrows(Exception.class, () ->
        warehouseTransferService.approveTransfer(CORR_ID, LOG_KEY, t1.getId()),
        "Repeated approval must be rejected");

    // Complete transfer from APPROVED
    var completedT1 = warehouseTransferService.completeTransfer(CORR_ID, LOG_KEY, t1.getId());
    assertEquals("COMPLETED", completedT1.getStatus());
    assertNotNull(completedT1.getApprovedAt(), "approved_at must remain preserved");
    assertNotNull(completedT1.getCompletedAt(), "completed_at must be populated upon completion");
    assertNull(completedT1.getCancelledAt());

    // Completed transfer cannot be approved or cancelled
    assertThrows(Exception.class, () ->
        warehouseTransferService.approveTransfer(CORR_ID, LOG_KEY, t1.getId()));
    assertThrows(Exception.class, () ->
        warehouseTransferService.cancelTransfer(CORR_ID, LOG_KEY, t1.getId(), "Cancel after complete"));

    // Case 2: INITIATED -> COMPLETED (direct completion)
    var t2 = warehouseTransferService.initiateTransfer(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseTransferDto.builder()
        .sourceWarehouseId(whA.getId())
        .destWarehouseId(whB.getId())
        .productId(prod.getId())
        .qty(20)
        .referenceNote("Direct completion test")
        .build());

    var completedT2 = warehouseTransferService.completeTransfer(CORR_ID, LOG_KEY, t2.getId());
    assertEquals("COMPLETED", completedT2.getStatus());
    assertNotNull(completedT2.getCompletedAt());
    assertNull(completedT2.getApprovedAt(), "Direct completion does not populate approved_at");
    assertNull(completedT2.getCancelledAt());

    // Case 3: INITIATED -> CANCELLED
    var t3 = warehouseTransferService.initiateTransfer(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseTransferDto.builder()
        .sourceWarehouseId(whA.getId())
        .destWarehouseId(whB.getId())
        .productId(prod.getId())
        .qty(10)
        .referenceNote("Cancellation test")
        .build());

    var cancelledT3 = warehouseTransferService.cancelTransfer(CORR_ID, LOG_KEY, t3.getId(), "No longer needed");
    assertEquals("CANCELLED", cancelledT3.getStatus());
    assertNotNull(cancelledT3.getCancelledAt(), "cancelled_at must be populated");
    assertNull(cancelledT3.getCompletedAt(), "cancelled transfer must not populate completed_at");
    assertNull(cancelledT3.getApprovedAt());

    // Cancelled transfer cannot be completed or approved
    assertThrows(Exception.class, () ->
        warehouseTransferService.completeTransfer(CORR_ID, LOG_KEY, t3.getId()),
        "Cancelled transfer cannot be completed");
    assertThrows(Exception.class, () ->
        warehouseTransferService.approveTransfer(CORR_ID, LOG_KEY, t3.getId()),
        "Cancelled transfer cannot be approved");
  }

  @Test
  @DisplayName("33. Batch & Lot Domain: Batch CRUD, FEFO ordering, and Tenant Isolation")
  void testProductBatch_CrudAndTenantIsolation() throws Exception {
    long userA = createTestUser("batch_a_" + System.currentTimeMillis() + "@test.com", "Batch User A");
    long userB = createTestUser("batch_b_" + System.currentTimeMillis() + "@test.com", "Batch User B");

    setAuthenticatedUser(userA, "batch_a@test.com", "USER");
    Category cat = Category.builder().name("BatchCat_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prodDto = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Batch Item " + System.currentTimeMillis())
        .sku("SKU-BATCH-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(100)
        .build());

    // 1. Create two batches for User A: one expiring earlier, one expiring later
    long batchId1 = productBatchRepository.save(CORR_ID, LOG_KEY, com.stockiq.inventory.entity.ProductBatch.builder()
        .userId(userA)
        .productId(prodDto.getId())
        .batchNumber("LOT-2026-001")
        .mfgDate(java.sql.Date.valueOf("2026-01-01"))
        .expiryDate(java.sql.Date.valueOf("2026-12-31"))
        .costPrice(new java.math.BigDecimal("15.5000"))
        .status("ACTIVE")
        .build());
    assertTrue(batchId1 > 0);

    long batchId2 = productBatchRepository.save(CORR_ID, LOG_KEY, com.stockiq.inventory.entity.ProductBatch.builder()
        .userId(userA)
        .productId(prodDto.getId())
        .batchNumber("LOT-2026-002")
        .mfgDate(java.sql.Date.valueOf("2026-02-01"))
        .expiryDate(java.sql.Date.valueOf("2026-06-30"))
        .costPrice(new java.math.BigDecimal("16.0000"))
        .status("ACTIVE")
        .build());
    assertTrue(batchId2 > 0);

    // 2. Query batches for product: verify FEFO order (LOT-2026-002 expiring 2026-06-30 comes first)
    var batches = productBatchRepository.findByProductId(CORR_ID, LOG_KEY, prodDto.getId());
    assertEquals(2, batches.size());
    assertEquals("LOT-2026-002", batches.get(0).getBatchNumber());
    assertEquals("LOT-2026-001", batches.get(1).getBatchNumber());

    // 3. Duplicate batch number for same product and user must fail
    assertThrows(Exception.class, () ->
        productBatchRepository.save(CORR_ID, LOG_KEY, com.stockiq.inventory.entity.ProductBatch.builder()
            .userId(userA)
            .productId(prodDto.getId())
            .batchNumber("LOT-2026-001")
            .build()));

    // 4. Cross-tenant isolation: User B cannot find User A's batch
    setAuthenticatedUser(userB, "batch_b@test.com", "USER");
    assertTrue(productBatchRepository.findById(CORR_ID, LOG_KEY, batchId1).isEmpty());
    assertTrue(productBatchRepository.findByProductId(CORR_ID, LOG_KEY, prodDto.getId()).isEmpty());
  }

  @Test
  @DisplayName("34. Batch Inventory: 3-Column Composite FK Enforces Batch-to-Product Consistency")
  void testBatchInventory_CompositeForeignKeyIntegrity() throws Exception {
    long user = createTestUser("batch_inv_" + System.currentTimeMillis() + "@test.com", "Batch Inv User");
    setAuthenticatedUser(user, "batch_inv@test.com", "USER");

    Category cat = Category.builder().name("BatchInvCat_" + System.currentTimeMillis()).icon("box").status(1).build();
    categoryRepository.save(CORR_ID, LOG_KEY, cat);

    ProductDto prodA = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Batch Prod A " + System.currentTimeMillis())
        .sku("SKU-BINV-A-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(50)
        .build());

    ProductDto prodB = productService.save(CORR_ID, LOG_KEY, ProductDto.builder()
        .name("Batch Prod B " + System.currentTimeMillis())
        .sku("SKU-BINV-B-" + System.currentTimeMillis())
        .category(cat.getName())
        .qty(50)
        .build());

    var wh = warehouseService.create(CORR_ID, LOG_KEY, com.stockiq.inventory.dto.WarehouseDto.builder()
        .code("WH-BATCH-" + System.currentTimeMillis())
        .name("Batch Test Warehouse")
        .build());

    // Batch 1 belongs to Product A
    long batchAId = productBatchRepository.save(CORR_ID, LOG_KEY, com.stockiq.inventory.entity.ProductBatch.builder()
        .userId(user)
        .productId(prodA.getId())
        .batchNumber("BATCH-A-01")
        .status("ACTIVE")
        .build());

    // Valid batch inventory: Batch A, Warehouse, Product A
    long binvId = batchInventoryRepository.save(CORR_ID, LOG_KEY, com.stockiq.inventory.entity.BatchInventory.builder()
        .userId(user)
        .batchId(batchAId)
        .warehouseId(wh.getId())
        .productId(prodA.getId())
        .qty(25)
        .build());
    assertTrue(binvId > 0);

    // Verify aggregate stock
    assertEquals(25, batchInventoryRepository.getAggregateBatchStock(CORR_ID, LOG_KEY, batchAId));

    // INVALID: Try to insert batch_inventory referencing Batch A (owned by Product A) with Product B!
    // The composite FK (batch_id, product_id, user_id) -> product_batches(id, product_id, user_id) MUST reject this!
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
        batchInventoryRepository.save(CORR_ID, LOG_KEY, com.stockiq.inventory.entity.BatchInventory.builder()
            .userId(user)
            .batchId(batchAId)
            .warehouseId(wh.getId())
            .productId(prodB.getId()) // MISMATCH: Batch belongs to Prod A, but row says Prod B!
            .qty(10)
            .build()),
        "PostgreSQL composite foreign key fk_batch_inv_batch_prod_user MUST reject mismatching batch and product IDs");
  }
}
