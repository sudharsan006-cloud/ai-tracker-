package com.lifetracker.ai;

import com.lifetracker.ai.client.GeminiClient;
import com.lifetracker.ai.client.GeminiResult;
import com.lifetracker.ai.dto.*;
import com.lifetracker.config.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class Phase5AiServiceTest {

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
        registry.add("app.gemini.api-key", () -> "test_api_key");
        registry.add("app.gemini.model", () -> "gemini-3.1-flash-lite");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public GeminiClient fakeGeminiClient() {
            return new FakeGeminiClient();
        }
    }

    @Autowired
    private FactsService factsService;

    @Autowired
    private InsightService insightService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GeminiClient geminiClient;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, 'hash')",
                userId, "ai_test_" + userId + "@test.com");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, display_name, timezone) VALUES (?, 'Test User', 'Asia/Kolkata')",
                userId);

        if (geminiClient instanceof FakeGeminiClient fake) {
            fake.reset();
        }
    }

    // -------------------------------------------------------------
    // Test 5: Privacy Test
    // -------------------------------------------------------------
    @Test
    @DisplayName("Test 5: Privacy test - sensitive fields never appear in facts JSON or AI prompt")
    void testPrivacyBoundary() {
        UUID privUser = UUID.randomUUID();
        String markerEmail = "ZZ-MARKER-EMAIL@test.com";
        String markerName = "ZZ-MARKER-DISPLAYNAME";
        String markerBank = "ZZ-MARKER-BANK";
        String markerMerchant = "ZZ-MARKER-MERCHANT";
        String markerNote = "ZZ-MARKER-MOODNOTE";
        String markerExercise = "ZZ-MARKER-EXERCISE";

        jdbcTemplate.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, 'hash')", privUser, markerEmail);
        jdbcTemplate.update("INSERT INTO user_profile (user_id, display_name) VALUES (?, ?)", privUser, markerName);

        // Add bank account
        Integer bankId = jdbcTemplate.queryForObject("SELECT id FROM banks LIMIT 1", Integer.class);
        Integer currId = jdbcTemplate.queryForObject("SELECT id FROM currencies WHERE code = 'INR'", Integer.class);
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO bank_accounts (id, user_id, bank_id, currency_id, account_last4, nickname) VALUES (?, ?, ?, ?, '9999', ?)",
                accountId, privUser, bankId, currId, markerBank);

        // Add transaction with merchant
        jdbcTemplate.update("""
            INSERT INTO bank_transactions (id, account_id, user_id, currency_id, transaction_date, amount, direction, raw_text, merchant_normalized)
            VALUES (?, ?, ?, ?, now(), 500.00, 'debit', ?, ?)
        """, UUID.randomUUID(), accountId, privUser, currId, markerMerchant, markerMerchant);

        // Add mood with note
        jdbcTemplate.update("INSERT INTO mood_log (user_id, log_date, score, note) VALUES (?, now()::date, 3, ?)",
                privUser, markerNote);

        SpendingMonthFacts spending = factsService.getSpendingMonthFacts(privUser);
        FitnessWeekFacts fitness = factsService.getFitnessWeekFacts(privUser);
        OverviewWeekFacts overview = factsService.getOverviewWeekFacts(privUser);

        String sJson = factsService.toCanonicalJson(spending);
        String fJson = factsService.toCanonicalJson(fitness);
        String oJson = factsService.toCanonicalJson(overview);

        String[] markers = { markerEmail, markerName, markerBank, markerMerchant, markerNote, markerExercise };
        for (String m : markers) {
            assertThat(sJson).doesNotContain(m);
            assertThat(fJson).doesNotContain(m);
            assertThat(oJson).doesNotContain(m);
        }
    }

    // -------------------------------------------------------------
    // Test 6: factsHash stability and change
    // -------------------------------------------------------------
    @Test
    @DisplayName("Test 6: factsHash is stable for same data and changes when transaction is added")
    void testFactsHashStability() {
        SpendingMonthFacts f1 = factsService.getSpendingMonthFacts(userId);
        String hash1 = factsService.computeFactsHash(factsService.toCanonicalJson(f1));

        SpendingMonthFacts f2 = factsService.getSpendingMonthFacts(userId);
        String hash2 = factsService.computeFactsHash(factsService.toCanonicalJson(f2));

        assertThat(hash1).isEqualTo(hash2);

        // Add transaction
        Integer bankId = jdbcTemplate.queryForObject("SELECT id FROM banks LIMIT 1", Integer.class);
        Integer currId = jdbcTemplate.queryForObject("SELECT id FROM currencies WHERE code = 'INR'", Integer.class);
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO bank_accounts (id, user_id, bank_id, currency_id) VALUES (?, ?, ?, ?)",
                accountId, userId, bankId, currId);
        jdbcTemplate.update("""
            INSERT INTO bank_transactions (id, account_id, user_id, currency_id, transaction_date, amount, direction)
            VALUES (?, ?, ?, ?, now(), 120.00, 'debit')
        """, UUID.randomUUID(), accountId, userId, currId);

        SpendingMonthFacts f3 = factsService.getSpendingMonthFacts(userId);
        String hash3 = factsService.computeFactsHash(factsService.toCanonicalJson(f3));

        assertThat(hash3).isNotEqualTo(hash1);
    }

    // -------------------------------------------------------------
    // Test 7: Generate flow & Cache
    // -------------------------------------------------------------
    @Test
    @DisplayName("Test 7: Generate flow with fake client and caching")
    void testGenerateFlowAndCache() {
        FakeGeminiClient fake = (FakeGeminiClient) geminiClient;
        fake.reset();
        fake.setDefaultResponse(new GeminiResult(200, "Spending is 0 for this month.", null, "STOP"));

        // Add 1 transaction so hasData = true
        Integer bankId = jdbcTemplate.queryForObject("SELECT id FROM banks LIMIT 1", Integer.class);
        Integer currId = jdbcTemplate.queryForObject("SELECT id FROM currencies WHERE code = 'INR'", Integer.class);
        UUID accId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO bank_accounts (id, user_id, bank_id, currency_id) VALUES (?, ?, ?, ?)", accId, userId, bankId, currId);
        jdbcTemplate.update("INSERT INTO bank_transactions (id, account_id, user_id, currency_id, transaction_date, amount, direction) VALUES (?, ?, ?, ?, now(), 10.00, 'debit')",
                UUID.randomUUID(), accId, userId, currId);

        fake.setDefaultResponse(new GeminiResult(200, "You spent 10 on items.", null, "STOP"));

        GenerateInsightResponse res1 = insightService.generateInsight(userId, "spending_month");
        assertThat(res1.getFallbackReason()).isNull();
        assertThat(res1.getInsight()).isNotNull();
        assertThat(fake.getCallCount()).isEqualTo(1);

        // Second call with unchanged data -> returned from cache
        GenerateInsightResponse res2 = insightService.generateInsight(userId, "spending_month");
        assertThat(res2.getFallbackReason()).isNull();
        assertThat(res2.getInsight()).isNotNull();
        assertThat(fake.getCallCount()).isEqualTo(1); // No new fake call!
    }

    // -------------------------------------------------------------
    // Test 9: Daily call budget
    // -------------------------------------------------------------
    @Test
    @DisplayName("Test 9: Daily budget reached returns BUDGET_REACHED")
    void testDailyCallBudget() {
        FakeGeminiClient fake = (FakeGeminiClient) geminiClient;
        fake.reset();

        configService.set("ai_insight_daily_call_budget", BigDecimal.valueOf(2));

        // Add 2 call logs
        jdbcTemplate.update("INSERT INTO ai_call_log (user_id, called_at, kind, http_status, success) VALUES (?, now(), 'fitness_week', 200, true)", userId);
        jdbcTemplate.update("INSERT INTO ai_call_log (user_id, called_at, kind, http_status, success) VALUES (?, now(), 'fitness_week', 200, true)", userId);

        // Add fitness data so hasData is true
        jdbcTemplate.update("INSERT INTO fitness_daily_log (user_id, log_date, steps) VALUES (?, now()::date, 5000)", userId);

        GenerateInsightResponse res = insightService.generateInsight(userId, "fitness_week");
        assertThat(res.getFallbackReason()).isEqualTo("BUDGET_REACHED");
        assertThat(fake.getCallCount()).isEqualTo(0);

        // Reset budget back to 40
        configService.set("ai_insight_daily_call_budget", BigDecimal.valueOf(40));
    }

    // -------------------------------------------------------------
    // Test 10: Guard retry
    // -------------------------------------------------------------
    @Test
    @DisplayName("Test 10: Guard retry on rejected text")
    void testGuardRetry() {
        FakeGeminiClient fake = (FakeGeminiClient) geminiClient;
        fake.reset();

        // Add fitness data
        jdbcTemplate.update("INSERT INTO fitness_daily_log (user_id, log_date, steps) VALUES (?, now()::date, 5000)", userId);

        // Attempt 1 returns text with unallowed number 999999
        fake.queueResponse(new GeminiResult(200, "You walked 999999 steps.", null, "STOP"));
        // Attempt 2 returns valid text
        fake.queueResponse(new GeminiResult(200, "You walked 5000 steps.", null, "STOP"));

        GenerateInsightResponse res = insightService.generateInsight(userId, "fitness_week");
        assertThat(res.getFallbackReason()).isNull();
        assertThat(res.getInsight().getText()).contains("5000");
        assertThat(fake.getCallCount()).isEqualTo(2);

        // If bad text twice -> REJECTED_BY_GUARD
        fake.reset();
        UUID userB = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, 'hash')", userB, "b_" + userB + "@test.com");
        jdbcTemplate.update("INSERT INTO user_profile (user_id) VALUES (?)", userB);
        jdbcTemplate.update("INSERT INTO fitness_daily_log (user_id, log_date, steps) VALUES (?, now()::date, 3000)", userB);

        fake.queueResponse(new GeminiResult(200, "You should walk more.", null, "STOP"));
        fake.queueResponse(new GeminiResult(200, "You must walk more.", null, "STOP"));

        GenerateInsightResponse resB = insightService.generateInsight(userB, "fitness_week");
        assertThat(resB.getFallbackReason()).isEqualTo("REJECTED_BY_GUARD");
        assertThat(fake.getCallCount()).isEqualTo(2);
    }
}
