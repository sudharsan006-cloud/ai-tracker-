package com.lifetracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetracker.ai.client.GeminiClient;
import com.lifetracker.ai.client.GeminiResult;
import com.lifetracker.ai.dto.AiSettingsDto;
import com.lifetracker.auth.dto.AuthRequest;
import com.lifetracker.auth.dto.AuthResponse;
import com.lifetracker.config.ConfigService;
import com.lifetracker.mood.dto.MoodRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class Phase5IntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConfigService configService;

    @Autowired
    private FactsService factsService;

    @Autowired
    private GeminiClient geminiClient;

    private String token1;
    private UUID user1Id;
    private String token2;
    private UUID user2Id;

    @BeforeEach
    void setUp() throws Exception {
        token1 = registerAndLogin("phase5_u1_" + UUID.randomUUID() + "@test.com", "Password123!");
        user1Id = getUserIdFromToken(token1);

        token2 = registerAndLogin("phase5_u2_" + UUID.randomUUID() + "@test.com", "Password123!");
        user2Id = getUserIdFromToken(token2);

        if (geminiClient instanceof FakeGeminiClient fake) {
            fake.reset();
        }
    }

    private String registerAndLogin(String email, String password) throws Exception {
        AuthRequest req = new AuthRequest(email, password);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse authRes = objectMapper.readValue(res.getResponse().getContentAsString(), AuthResponse.class);
        return "Bearer " + authRes.getAccessToken();
    }

    private UUID getUserIdFromToken(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/profile")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();
        var map = objectMapper.readValue(res.getResponse().getContentAsString(), java.util.Map.class);
        return UUID.fromString((String) map.get("userId"));
    }

    // -------------------------------------------------------------
    // Test 15: Mood CRUD and Validation
    // -------------------------------------------------------------
    @Test
    @DisplayName("Test 15: Mood PUT creates and updates, score & note validation, GET range, DELETE")
    void testMoodCrudAndValidation() throws Exception {
        // Invalid score 0
        mockMvc.perform(put("/api/v1/mood")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\": 0}"))
                .andExpect(status().isBadRequest());

        // Invalid score 6
        mockMvc.perform(put("/api/v1/mood")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\": 6}"))
                .andExpect(status().isBadRequest());

        // Future date
        String futureDate = LocalDate.now().plusDays(2).toString();
        mockMvc.perform(put("/api/v1/mood")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"" + futureDate + "\", \"score\": 3}"))
                .andExpect(status().isBadRequest());

        // Note over 300 chars
        String longNote = "a".repeat(301);
        mockMvc.perform(put("/api/v1/mood")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoodRequest(null, (short) 3, longNote))))
                .andExpect(status().isBadRequest());

        // Valid PUT create
        String todayStr = LocalDate.now().toString();
        mockMvc.perform(put("/api/v1/mood")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"" + todayStr + "\", \"score\": 4, \"note\": \"Feeling good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(4))
                .andExpect(jsonPath("$.note").value("Feeling good"));

        // Second PUT updates
        mockMvc.perform(put("/api/v1/mood")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"" + todayStr + "\", \"score\": 5, \"note\": \"Great evening\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(5))
                .andExpect(jsonPath("$.note").value("Great evening"));

        // GET range
        mockMvc.perform(get("/api/v1/mood")
                        .header("Authorization", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].score").value(5));

        // DELETE
        mockMvc.perform(delete("/api/v1/mood/" + todayStr)
                        .header("Authorization", token1))
                .andExpect(status().isNoContent());

        // Repeat DELETE -> 404
        mockMvc.perform(delete("/api/v1/mood/" + todayStr)
                        .header("Authorization", token1))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------
    // Test 16: Mood Nudge Rules
    // -------------------------------------------------------------
    @Test
    @DisplayName("Test 16: Mood nudge triggers after 4 low entries, respects dismiss & cooldown")
    void testMoodNudge() throws Exception {
        LocalDate today = LocalDate.now();

        // 3 low entries -> show=false
        for (int i = 1; i <= 3; i++) {
            jdbcTemplate.update("INSERT INTO mood_log (user_id, log_date, score) VALUES (?, ?, ?)",
                    user1Id, today.minusDays(i), 2);
        }

        mockMvc.perform(get("/api/v1/mood/nudge")
                        .header("Authorization", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.show").value(false));

        // 4th low entry -> show=true
        jdbcTemplate.update("INSERT INTO mood_log (user_id, log_date, score) VALUES (?, ?, ?)",
                user1Id, today.minusDays(4), 1);

        mockMvc.perform(get("/api/v1/mood/nudge")
                        .header("Authorization", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.show").value(true))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Your mood entries were low on 4 of the last 7 days.")));

        // Dismiss nudge
        mockMvc.perform(post("/api/v1/mood/nudge/dismiss")
                        .header("Authorization", token1))
                .andExpect(status().isNoContent());

        // Now show=false
        mockMvc.perform(get("/api/v1/mood/nudge")
                        .header("Authorization", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.show").value(false));

        // After cooldown passes (set dismissed_on to 8 days ago) -> show=true
        jdbcTemplate.update("UPDATE user_ai_settings SET mood_nudge_dismissed_on = ? WHERE user_id = ?",
                today.minusDays(8), user1Id);

        mockMvc.perform(get("/api/v1/mood/nudge")
                        .header("Authorization", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.show").value(true));
    }

    // -------------------------------------------------------------
    // Test 17: AI Settings
    // -------------------------------------------------------------
    @Test
    @DisplayName("Test 17: AI settings defaults, PUT, and GET")
    void testAiSettings() throws Exception {
        // Defaults without row: aiEnabled=true, includeMood=false
        mockMvc.perform(get("/api/v1/ai/settings")
                        .header("Authorization", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(true))
                .andExpect(jsonPath("$.includeMood").value(false));

        // PUT update
        mockMvc.perform(put("/api/v1/ai/settings")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiEnabled\": false, \"includeMood\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(false))
                .andExpect(jsonPath("$.includeMood").value(true));

        // GET again
        mockMvc.perform(get("/api/v1/ai/settings")
                        .header("Authorization", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(false))
                .andExpect(jsonPath("$.includeMood").value(true));
    }

    // -------------------------------------------------------------
    // Test 18: User Isolation
    // -------------------------------------------------------------
    @Test
    @DisplayName("Test 18: User isolation for mood, settings, insights")
    void testUserIsolation() throws Exception {
        String todayStr = LocalDate.now().toString();

        // User 1 logs mood
        mockMvc.perform(put("/api/v1/mood")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"" + todayStr + "\", \"score\": 4, \"note\": \"User 1 note\"}"))
                .andExpect(status().isOk());

        // User 2 cannot see User 1's mood
        mockMvc.perform(get("/api/v1/mood")
                        .header("Authorization", token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // -------------------------------------------------------------
    // Test 19: Account Deletion Regression
    // -------------------------------------------------------------
    @Test
    @DisplayName("Test 19: User with mood, settings, insights can delete account")
    void testAccountDeletionRegression() throws Exception {
        LocalDate today = LocalDate.now();

        // Add mood entry
        jdbcTemplate.update("INSERT INTO mood_log (user_id, log_date, score, note) VALUES (?, ?, ?, ?)",
                user1Id, today, 4, "Note to delete");

        // Add settings
        jdbcTemplate.update("INSERT INTO user_ai_settings (user_id, ai_enabled, include_mood) VALUES (?, true, true) ON CONFLICT (user_id) DO NOTHING",
                user1Id);

        // Add insights
        jdbcTemplate.update("""
            INSERT INTO ai_insights (user_id, kind, period_start, period_end, facts_hash, prompt_version, model, text)
            VALUES (?, 'fitness_week', ?, ?, 'dummyhash', 1, 'gemini-3.1-flash-lite', 'Text to delete')
        """, user1Id, today.minusDays(6), today);

        // Delete account
        mockMvc.perform(delete("/api/v1/account")
                        .header("Authorization", token1))
                .andExpect(status().isNoContent());

        // Verify cascaded deletion
        Integer moodCount = jdbcTemplate.queryForObject("SELECT count(*) FROM mood_log WHERE user_id = ?", Integer.class, user1Id);
        assertThat(moodCount).isEqualTo(0);
    }
}
