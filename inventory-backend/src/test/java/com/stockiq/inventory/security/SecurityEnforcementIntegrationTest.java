package com.stockiq.inventory.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockiq.inventory.common.util.JwtUtil;
import com.stockiq.inventory.common.util.PageResponse;
import com.stockiq.inventory.common.response.user.LoginResponse;
import com.stockiq.inventory.dto.AuditTrailDto;
import com.stockiq.inventory.dto.ProductDto;
import com.stockiq.inventory.service.AuditTrailService;
import com.stockiq.inventory.service.AuthService;
import com.stockiq.inventory.service.ProductService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Security & Authentication Enforcement Integration Tests")
class SecurityEnforcementIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private FilterChainProxy springSecurityFilterChain;

  @Autowired private JwtUtil jwtUtil;

  @MockitoBean private ProductService productService;

  @MockitoBean private AuditTrailService auditTrailService;

  @MockitoBean private AuthService authService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    this.mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(springSecurityFilterChain)
            .build();
  }

  @Test
  @DisplayName("Unauthenticated request to protected endpoint /products returns 401 Unauthorized")
  void unauthenticated_requestToProducts_returns401() throws Exception {
    mockMvc.perform(get("/products"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.statusCode").value(401))
        .andExpect(jsonPath("$.errorCode").value("AUTH_401"));
  }

  @Test
  @DisplayName("Unauthenticated request to protected endpoint /categories returns 401 Unauthorized")
  void unauthenticated_requestToCategories_returns401() throws Exception {
    mockMvc.perform(get("/categories"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.statusCode").value(401))
        .andExpect(jsonPath("$.errorCode").value("AUTH_401"));
  }

  @Test
  @DisplayName("Unauthenticated request to protected endpoint /audit-trail returns 401 Unauthorized")
  void unauthenticated_requestToAuditTrail_returns401() throws Exception {
    mockMvc.perform(get("/audit-trail"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.statusCode").value(401))
        .andExpect(jsonPath("$.errorCode").value("AUTH_401"));
  }

  @Test
  @DisplayName("Unauthenticated request to protected endpoint /dashboard/summary returns 401 Unauthorized")
  void unauthenticated_requestToDashboard_returns401() throws Exception {
    mockMvc.perform(get("/dashboard/summary"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.statusCode").value(401))
        .andExpect(jsonPath("$.errorCode").value("AUTH_401"));
  }

  @Test
  @DisplayName("Request with invalid JWT returns 401 Unauthorized")
  void invalidJwt_returns401() throws Exception {
    mockMvc.perform(get("/products").header("Authorization", "Bearer invalid.jwt.token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.statusCode").value(401))
        .andExpect(jsonPath("$.errorCode").value("AUTH_401"));
  }

  @Test
  @DisplayName("Request with expired JWT returns 401 Unauthorized")
  void expiredJwt_returns401() throws Exception {
    String expiredToken =
        io.jsonwebtoken.Jwts.builder()
            .subject("user@example.com")
            .claim("role", "USER")
            .claim("userId", 1L)
            .issuedAt(new java.util.Date(System.currentTimeMillis() - 100000))
            .expiration(new java.util.Date(System.currentTimeMillis() - 50000))
            .signWith(
                io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                    "test_super_secret_key_for_testing_purposes_only_at_least_32_chars"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .compact();

    mockMvc.perform(get("/products").header("Authorization", "Bearer " + expiredToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.statusCode").value(401))
        .andExpect(jsonPath("$.errorCode").value("AUTH_401"));
  }

  @Test
  @DisplayName("Authenticated user with USER role accessing /audit-trail returns 403 Forbidden")
  void userRole_accessingAuditTrail_returns403() throws Exception {
    String userToken = jwtUtil.generateToken("user@example.com", "USER", 100L);

    mockMvc.perform(get("/audit-trail").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("AUTH_403"));
  }

  @Test
  @DisplayName("Authenticated user with ADMIN role accessing /audit-trail is permitted")
  void adminRole_accessingAuditTrail_isPermitted() throws Exception {
    String adminToken = jwtUtil.generateToken("admin@example.com", "ADMIN", 1L);

    when(auditTrailService.getAuditTrail(
            anyString(), anyString(), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(new PageResponse<>(List.of(), 1, 10, 0));

    mockMvc.perform(get("/audit-trail").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Authenticated user can access /products successfully")
  void authenticatedUser_canAccessProducts() throws Exception {
    String userToken = jwtUtil.generateToken("user@example.com", "USER", 100L);

    when(productService.findAll(anyString(), anyString(), anyInt(), anyInt(), any(), any()))
        .thenReturn(new PageResponse<>(List.of(), 1, 10, 0));

    mockMvc.perform(get("/products").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Public endpoint /auth/login is accessible without authentication filter 401")
  void publicEndpoint_login_isAccessibleWithoutAuth() throws Exception {
    when(authService.login(anyString(), anyString(), any()))
        .thenReturn(LoginResponse.builder().email("test@user.com").token("dummy").build());

    mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@user.com\",\"password\":\"password123\"}"))
        // Filter permits the request and it reaches controller/service
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Public endpoint /auth/register is accessible without authentication filter 401")
  void publicEndpoint_register_isAccessibleWithoutAuth() throws Exception {
    mockMvc.perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        // Filter permits request, validation exception handler returns 400 Bad Request
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Public endpoint /auth/forgot-password is accessible without auth and returns 200 without leaking account existence")
  void forgotPassword_publicAndAntiEnumeration() throws Exception {
    when(authService.requestPasswordReset(anyString(), anyString(), any()))
        .thenReturn("sample-dev-token");

    mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"random@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("If the email exists, a password reset token has been dispatched."));
  }

  @Test
  @DisplayName("Public endpoint /auth/reset-password is accessible without auth, validates token via service")
  void resetPassword_publicEndpoint_executesReset() throws Exception {
    when(authService.resetPassword(anyString(), anyString(), any()))
        .thenReturn(true);

    mockMvc.perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"any-token-string\",\"newPassword\":\"NewSecurePassword123!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Password has been successfully reset."));
  }

  @Test
  @DisplayName("Production mode /auth/forgot-password does not return resetToken in JSON body")
  void forgotPassword_prodMode_doesNotExposeToken() throws Exception {
    when(authService.requestPasswordReset(anyString(), anyString(), any()))
        .thenReturn("");

    mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"realuser@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("If the email exists, a password reset token has been dispatched."))
        .andExpect(jsonPath("$.resetToken").doesNotExist());
  }
}
