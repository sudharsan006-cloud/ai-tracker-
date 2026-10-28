package com.lifetracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetracker.config.RateLimitFilter;
import com.lifetracker.config.SecurityHeadersFilter;
import com.lifetracker.export.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class Phase6IntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb_phase6")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", postgres::getJdbcUrl);
        registry.add("DB_USER", postgres::getUsername);
        registry.add("DB_PASSWORD", postgres::getPassword);
        registry.add("JWT_SECRET", () -> "a_very_secure_test_jwt_secret_with_at_least_32_characters_for_phase6_tests!");
        registry.add("APP_ALLOWED_ORIGINS", () -> "http://localhost:5000,https://trusted.example.com");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private ExportService exportService;

    @BeforeEach
    void setup() {
        rateLimitFilter.reset();
    }

    @Test
    @DisplayName("A2: GET /api/v1/health returns status ok with no auth and no db query")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("A3: CORS hardening - evil origin is rejected, allowed origin has CORS header")
    void testCorsHardening() throws Exception {
        // Disallowed origin gets NO Access-Control-Allow-Origin header
        mockMvc.perform(get("/api/v1/health")
                        .header("Origin", "https://evil.example"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        // Allowed origin gets Access-Control-Allow-Origin header
        mockMvc.perform(get("/api/v1/health")
                        .header("Origin", "https://trusted.example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://trusted.example.com"));
    }

    @Test
    @DisplayName("A4: RateLimitFilter blocks 11th request with HTTP 429")
    void testRateLimiterOnAuthEndpoints() throws Exception {
        String testIp = "192.168.1.100";
        String loginBody = "{\"email\":\"nobody@example.com\",\"password\":\"wrongpass123\"}";

        // First 10 requests should proceed (resulting in 401 Unauthorized, not 429)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody))
                    .andExpect(status().isUnauthorized());
        }

        // 11th request must be blocked with HTTP 429 RATE_LIMITED
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("Too many requests. Try again in a minute."));

        // When rate limit is reset, request proceeds again
        rateLimitFilter.reset();
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A5: Security headers are present on /api/v1 responses")
    void testSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("A6: Data Export contains no sensitive keys like password, refresh, fingerprint")
    void testExportSafety() throws Exception {
        UUID testUserId = UUID.randomUUID();
        Map<String, Object> export = exportService.exportUserData(testUserId);

        assertThat(export).containsKey("exportedAt");
        assertThat(export).containsKey("profile");
        assertThat(export).containsKey("bankAccounts");
        assertThat(export).containsKey("categories");
        assertThat(export).containsKey("transactions");
        assertThat(export).containsKey("budgets");
        assertThat(export).containsKey("fitnessGoals");
        assertThat(export).containsKey("fitnessDaily");
        assertThat(export).containsKey("waterEntries");
        assertThat(export).containsKey("calorieEntries");
        assertThat(export).containsKey("workouts");
        assertThat(export).containsKey("moodLog");
        assertThat(export).containsKey("aiSettings");

        String json = objectMapper.writeValueAsString(export).toLowerCase();
        assertThat(json).doesNotContain("\"password\"");
        assertThat(json).doesNotContain("\"refresh\"");
        assertThat(json).doesNotContain("\"fingerprint\"");
    }
}
