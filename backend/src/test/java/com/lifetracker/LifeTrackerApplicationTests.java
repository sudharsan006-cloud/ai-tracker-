package com.lifetracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetracker.auth.dto.AuthRequest;
import com.lifetracker.auth.dto.AuthResponse;
import com.lifetracker.auth.dto.RefreshRequest;
import com.lifetracker.bank.dto.CreateBankAccountRequest;
import com.lifetracker.config.ConfigService;
import com.lifetracker.user.UserProfileRepository;
import com.lifetracker.user.UserRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class LifeTrackerApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", postgres::getJdbcUrl);
        registry.add("DB_USER", postgres::getUsername);
        registry.add("DB_PASSWORD", postgres::getPassword);
        registry.add("JWT_SECRET", () -> "a_very_secure_test_jwt_secret_with_at_least_32_characters_for_unit_tests!");
        registry.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:5000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConfigService configService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    @DisplayName("Test 1: Signup then login works; duplicate signup gives 409")
    void test1_signupAndLoginAndConflict() throws Exception {
        String email = "test1_" + UUID.randomUUID() + "@example.com";
        AuthRequest req = new AuthRequest(email, "password123");

        // 1. Signup works
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(900));

        // 2. Duplicate signup -> 409
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        // 3. Login works
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("Test 2: Refresh returns new tokens, and old refresh token stops working")
    void test2_refreshRotation() throws Exception {
        String email = "test2_" + UUID.randomUUID() + "@example.com";
        AuthRequest req = new AuthRequest(email, "password123");

        MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth1 = objectMapper.readValue(signupResult.getResponse().getContentAsString(), AuthResponse.class);

        // Refresh once
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(auth1.getRefreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthResponse auth2 = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), AuthResponse.class);

        // Old token cannot be used again
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(auth1.getRefreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Test 3: Reusing rotated refresh token -> 401 AND newest token of family also revoked")
    void test3_reuseDetectionRevokesFamily() throws Exception {
        String email = "test3_" + UUID.randomUUID() + "@example.com";
        AuthRequest req = new AuthRequest(email, "password123");

        MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth1 = objectMapper.readValue(signupResult.getResponse().getContentAsString(), AuthResponse.class);

        // Legitimate rotation: R1 -> R2
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(auth1.getRefreshToken()))))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth2 = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), AuthResponse.class);

        // Attacker / duplicate attempts to reuse R1 -> 401 and family revocation
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(auth1.getRefreshToken()))))
                .andExpect(status().isUnauthorized());

        // Now legitimate latest R2 is ALSO revoked
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(auth2.getRefreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Test 4: GET /banks and GET /currencies return seeded rows")
    void test4_banksAndCurrencies() throws Exception {
        String email = "test4_" + UUID.randomUUID() + "@example.com";
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readValue(signup.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();

        mockMvc.perform(get("/api/v1/banks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].shortCode").value("SAMPLE"));

        mockMvc.perform(get("/api/v1/currencies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].code").value("INR"));
    }

    @Test
    @DisplayName("Test 5: Creating bank account with non-existent bankId -> 400")
    void test5_invalidBankIdReturns400() throws Exception {
        String email = "test5_" + UUID.randomUUID() + "@example.com";
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readValue(signup.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();

        CreateBankAccountRequest req = new CreateBankAccountRequest();
        req.setBankId(999999);
        req.setCurrencyId(1);
        req.setAccountLast4("1234");
        req.setAccountType("savings");

        mockMvc.perform(post("/api/v1/bank-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Test 6: Creating more accounts than bank_account_max_per_user -> 409 LIMIT_REACHED")
    void test6_bankAccountLimitReached() throws Exception {
        String email = "test6_" + UUID.randomUUID() + "@example.com";
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readValue(signup.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();

        // Lower cap to 1
        configService.set("bank_account_max_per_user", BigDecimal.valueOf(1));

        CreateBankAccountRequest req = new CreateBankAccountRequest();
        req.setBankId(1);
        req.setCurrencyId(1);
        req.setAccountLast4("1111");
        req.setAccountType("savings");

        // 1st account succeeds
        mockMvc.perform(post("/api/v1/bank-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // 2nd account exceeds cap -> 409 LIMIT_REACHED
        CreateBankAccountRequest req2 = new CreateBankAccountRequest();
        req2.setBankId(1);
        req2.setCurrencyId(1);
        req2.setAccountLast4("2222");
        req2.setAccountType("savings");

        mockMvc.perform(post("/api/v1/bank-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LIMIT_REACHED"));

        // Reset cap
        configService.set("bank_account_max_per_user", BigDecimal.valueOf(20));
    }

    @Test
    @DisplayName("Test 7: ConfigService.set with value outside [min_value, max_value] throws IllegalArgumentException")
    void test7_configRangeValidation() {
        // bank_account_max_per_user min=1, max=100
        assertThatThrownBy(() -> configService.set("bank_account_max_per_user", BigDecimal.valueOf(101)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> configService.set("bank_account_max_per_user", BigDecimal.valueOf(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Test 8: Request without token to protected endpoint returns 401 (not 403)")
    void test8_unauthenticatedProtectedEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Test 9: DELETE /api/v1/account leaves no row in user_profile for that user")
    void test9_deleteAccountCascade() throws Exception {
        String email = "test9_" + UUID.randomUUID() + "@example.com";
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(signup.getResponse().getContentAsString(), AuthResponse.class);
        String token = auth.getAccessToken();

        MvcResult profileRes = mockMvc.perform(get("/api/v1/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        // Delete account
        mockMvc.perform(delete("/api/v1/account")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify user and profile rows are deleted
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }
}
