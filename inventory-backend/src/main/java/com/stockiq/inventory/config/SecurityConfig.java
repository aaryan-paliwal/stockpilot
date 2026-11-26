package com.stockiq.inventory.config;

import com.stockiq.inventory.common.util.JwtUtil;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtUtil jwtUtil;
  private final List<String> allowedOrigins;

  public SecurityConfig(
      JwtUtil jwtUtil,
      @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:5174}")
          String allowedOriginsStr) {
    this.jwtUtil = jwtUtil;
    this.allowedOrigins =
        Arrays.stream(allowedOriginsStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public JwtAuthenticationFilter jwtAuthenticationFilter() {
    return new JwtAuthenticationFilter(jwtUtil);
  }

  @Bean
  public AuthenticationEntryPoint restAuthenticationEntryPoint() {
    return (request, response, authException) -> {
      response.setContentType("application/json");
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.getWriter().write("""
          {
            "message": "Full authentication is required to access this resource",
            "title": "Unauthorized",
            "status": "UNAUTHORIZED",
            "statusCode": 401,
            "errorCode": "AUTH_401"
          }
          """);
    };
  }

  @Bean
  public AccessDeniedHandler restAccessDeniedHandler() {
    return (request, response, accessDeniedException) -> {
      response.setContentType("application/json");
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.getWriter().write("""
          {
            "message": "Access is denied: insufficient role or permissions",
            "title": "Forbidden",
            "status": "FORBIDDEN",
            "statusCode": 403,
            "errorCode": "AUTH_403"
          }
          """);
    };
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(allowedOrigins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id", "X-User-Email"));
    config.setExposedHeaders(List.of("Authorization"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(restAuthenticationEntryPoint())
                    .accessDeniedHandler(restAccessDeniedHandler()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/auth/register",
                        "/auth/login",
                        "/auth/forgot-password",
                        "/auth/reset-password",
                        "/auth/oauth/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
