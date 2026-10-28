package com.lifetracker.fitness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetracker.auth.dto.AuthRequest;
import com.lifetracker.auth.dto.AuthResponse;
import com.lifetracker.exercise.dto.ExercisePageResponse;
import com.lifetracker.exercise.dto.ExerciseSummary;
import com.lifetracker.fitness.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class Phase4IntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private String token1;
    private UUID user1Id;
    private String token2;
    private UUID user2Id;

    @BeforeEach
    void setUp() throws Exception {
        token1 = registerAndLogin("fituser1_" + UUID.randomUUID() + "@test.com", "Password123!");
        token2 = registerAndLogin("fituser2_" + UUID.randomUUID() + "@test.com", "Password123!");
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

    @Test
    @DisplayName("Test 5: Exercise seeder fills the table and is idempotent")
    void testExerciseSeederCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM exercises", Integer.class);
        assertThat(count).isNotNull().isGreaterThanOrEqualTo(150);
    }

    @Test
    @DisplayName("Test 6: Exercise list search, filters, pagination, and favorites")
    void testExerciseEndpoints() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/exercises?size=5")
                        .header("Authorization", token1))
                .andExpect(status().isOk())
                .andReturn();

        ExercisePageResponse page = objectMapper.readValue(res.getResponse().getContentAsString(), ExercisePageResponse.class);
        assertThat(page.getItems()).isNotEmpty();
        ExerciseSummary first = page.getItems().get(0);
        UUID exId = first.getId();

        // Favorite
        mockMvc.perform(post("/api/v1/exercises/" + exId + "/favorite")
                        .header("Authorization", token1))
                .andExpect(status().isNoContent());

        // Favoriting twice is fine
        mockMvc.perform(post("/api/v1/exercises/" + exId + "/favorite")
                        .header("Authorization", token1))
                .andExpect(status().isNoContent());

        // Check favoritesOnly=true
        mockMvc.perform(get("/api/v1/exercises?favoritesOnly=true")
                        .header("Authorization", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(exId.toString()))
                .andExpect(jsonPath("$.items[0].isFavorite").value(true));

        // Unfavorite
        mockMvc.perform(delete("/api/v1/exercises/" + exId + "/favorite")
                        .header("Authorization", token1))
                .andExpect(status().isNoContent());

        // Detail 404 for unknown
        mockMvc.perform(get("/api/v1/exercises/" + UUID.randomUUID())
                        .header("Authorization", token1))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Test 7: Steps rules logic")
    void testStepsRules() throws Exception {
        LocalDate today = LocalDate.now();

        // 1. Initial write
        StepsRequest r1 = new StepsRequest(today.toString(), 3000, "sensor");
        mockMvc.perform(put("/api/v1/fitness/daily/steps")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps").value(3000))
                .andExpect(jsonPath("$.stepsSource").value("sensor"));

        // 2. Sensor 5000 -> 5000
        StepsRequest r2 = new StepsRequest(today.toString(), 5000, "sensor");
        mockMvc.perform(put("/api/v1/fitness/daily/steps")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps").value(5000));

        // 3. Sensor 4000 lower -> stays 5000
        StepsRequest r3 = new StepsRequest(today.toString(), 4000, "sensor");
        mockMvc.perform(put("/api/v1/fitness/daily/steps")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps").value(5000));

        // 4. Sensor 6000 -> 6000
        StepsRequest r4 = new StepsRequest(today.toString(), 6000, "sensor");
        mockMvc.perform(put("/api/v1/fitness/daily/steps")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps").value(6000));

        // 5. Manual 7000 -> manual wins and stores 7000
        StepsRequest r5 = new StepsRequest(today.toString(), 7000, "manual");
        mockMvc.perform(put("/api/v1/fitness/daily/steps")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps").value(7000))
                .andExpect(jsonPath("$.stepsSource").value("manual"));

        // 6. Manual exists, sensor 8000 comes -> unchanged (manual wins)
        StepsRequest r6 = new StepsRequest(today.toString(), 8000, "sensor");
        mockMvc.perform(put("/api/v1/fitness/daily/steps")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps").value(7000))
                .andExpect(jsonPath("$.stepsSource").value("manual"));
    }

    @Test
    @DisplayName("Test 8: Date rules - tomorrow -> 400, 61 days ago -> 400")
    void testDateRules() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate oldDate = LocalDate.now().minusDays(61);

        StepsRequest rFuture = new StepsRequest(tomorrow.toString(), 1000, "manual");
        mockMvc.perform(put("/api/v1/fitness/daily/steps")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rFuture)))
                .andExpect(status().isBadRequest());

        StepsRequest rOld = new StepsRequest(oldDate.toString(), 1000, "manual");
        mockMvc.perform(put("/api/v1/fitness/daily/steps")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rOld)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 9: Water and Calories create, list newest first, delete, isolation and limits")
    void testWaterAndCalories() throws Exception {
        // Water
        WaterRequest wReq = new WaterRequest(250, null);
        MvcResult wRes = mockMvc.perform(post("/api/v1/fitness/water")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wReq)))
                .andExpect(status().isOk())
                .andReturn();
        WaterEntryResponse wEntry = objectMapper.readValue(wRes.getResponse().getContentAsString(), WaterEntryResponse.class);

        // Water amount limit 5001 -> 400
        mockMvc.perform(post("/api/v1/fitness/water")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WaterRequest(5001, null))))
                .andExpect(status().isBadRequest());

        // User2 cannot delete user1's water entry (404)
        mockMvc.perform(delete("/api/v1/fitness/water/" + wEntry.getId())
                        .header("Authorization", token2))
                .andExpect(status().isNotFound());

        // User1 deletes own entry -> 204
        mockMvc.perform(delete("/api/v1/fitness/water/" + wEntry.getId())
                        .header("Authorization", token1))
                .andExpect(status().isNoContent());

        // Calorie
        CalorieRequest cReq = new CalorieRequest(400, "Lunch salad", null);
        MvcResult cRes = mockMvc.perform(post("/api/v1/fitness/calories")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cReq)))
                .andExpect(status().isOk())
                .andReturn();
        CalorieEntryResponse cEntry = objectMapper.readValue(cRes.getResponse().getContentAsString(), CalorieEntryResponse.class);

        // Calorie limit 0 -> 400
        mockMvc.perform(post("/api/v1/fitness/calories")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CalorieRequest(0, null, null))))
                .andExpect(status().isBadRequest());

        // User2 cannot delete user1's calorie entry (404)
        mockMvc.perform(delete("/api/v1/fitness/calories/" + cEntry.getId())
                        .header("Authorization", token2))
                .andExpect(status().isNotFound());

        // User1 deletes own entry -> 204
        mockMvc.perform(delete("/api/v1/fitness/calories/" + cEntry.getId())
                        .header("Authorization", token1))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Test 10 & 11: Daily summary and range")
    void testDailySummaryAndRange() throws Exception {
        LocalDate today = LocalDate.now();

        mockMvc.perform(get("/api/v1/fitness/daily?date=" + today)
                        .header("Authorization", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepsGoal").value(8000))
                .andExpect(jsonPath("$.waterGoalMl").value(2000))
                .andExpect(jsonPath("$.steps").value(0))
                .andExpect(jsonPath("$.stepsPercent").value(0));

        // Range
        LocalDate from = today.minusDays(6);
        mockMvc.perform(get("/api/v1/fitness/range?from=" + from + "&to=" + today)
                        .header("Authorization", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isArray())
                .andExpect(jsonPath("$.averages.steps").isNumber());

        // Over 62 days -> 400
        mockMvc.perform(get("/api/v1/fitness/range?from=" + today.minusDays(63) + "&to=" + today)
                        .header("Authorization", token1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 12 & 13: Workouts create, PUT replace, DELETE cascade, and validation")
    void testWorkouts() throws Exception {
        // Library exercise
        UUID libraryExId = jdbcTemplate.queryForObject("SELECT id FROM exercises LIMIT 1", UUID.class);

        WorkoutEntryRequest e1 = new WorkoutEntryRequest(libraryExId, null, 3, 10, null, BigDecimal.valueOf(50));
        WorkoutEntryRequest e2 = new WorkoutEntryRequest(null, "Skipping", 1, null, 180, null);

        WorkoutCreateRequest createReq = new WorkoutCreateRequest(
                null, "Full Body Routine", 45, "moderate", "Felt good", List.of(e1, e2));

        MvcResult wRes = mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Full Body Routine"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andReturn();

        WorkoutResponse workout = objectMapper.readValue(wRes.getResponse().getContentAsString(), WorkoutResponse.class);

        // Validation test: both exerciseId and customName -> 400
        WorkoutEntryRequest invalidEntry = new WorkoutEntryRequest(libraryExId, "Bad Name", 3, 10, null, null);
        WorkoutCreateRequest invalidReq = new WorkoutCreateRequest(
                null, "Bad Routine", 30, "light", null, List.of(invalidEntry));
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());

        // Isolation: user2 cannot GET user1's workout
        mockMvc.perform(get("/api/v1/workouts/" + workout.getId())
                        .header("Authorization", token2))
                .andExpect(status().isNotFound());

        // User1 DELETE workout -> 204
        mockMvc.perform(delete("/api/v1/workouts/" + workout.getId())
                        .header("Authorization", token1))
                .andExpect(status().isNoContent());

        // Check entries were removed
        Integer entryCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM workout_entries WHERE workout_id = ?", Integer.class, workout.getId());
        assertThat(entryCount).isEqualTo(0);
    }
}
