package com.lifetracker.ai;

import com.lifetracker.ai.client.GeminiClient;
import com.lifetracker.ai.client.GeminiResult;
import com.lifetracker.ai.dto.*;
import com.lifetracker.ai.guard.InsightGuard;
import com.lifetracker.config.ConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class InsightService {

    public static final int PROMPT_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;
    private final FactsService factsService;
    private final ConfigService configService;
    private final AiSettingsService aiSettingsService;
    private final GeminiClient geminiClient;

    private final String configuredModel;
    private final String apiKey;
    private final String systemInstruction;

    private final AtomicLong lastRateLimitedTime = new AtomicLong(0);
    private long lastGeminiCallTime = 0;

    public InsightService(
            JdbcTemplate jdbcTemplate,
            FactsService factsService,
            ConfigService configService,
            AiSettingsService aiSettingsService,
            GeminiClient geminiClient,
            @Value("${app.gemini.model:gemini-3.1-flash-lite}") String configuredModel,
            @Value("${app.gemini.api-key:}") String apiKey) {
        this.jdbcTemplate = jdbcTemplate;
        this.factsService = factsService;
        this.configService = configService;
        this.aiSettingsService = aiSettingsService;
        this.geminiClient = geminiClient;
        this.configuredModel = configuredModel;
        this.apiKey = apiKey;
        this.systemInstruction = loadSystemInstruction();
    }

    private String loadSystemInstruction() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/insight_system_v1.txt");
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            return "You write a short plain-language summary of a person's own tracked numbers.";
        }
    }

    public void validateKind(String kind) {
        if (!"spending_month".equals(kind) && !"fitness_week".equals(kind) && !"overview_week".equals(kind)) {
            throw new IllegalArgumentException("Invalid kind: " + kind);
        }
    }

    public LocalDate getPeriodStart(LocalDate today, String kind) {
        if ("spending_month".equals(kind)) {
            return today.withDayOfMonth(1);
        } else {
            return today.minusDays(6);
        }
    }

    public LocalDate getPeriodEnd(LocalDate today) {
        return today;
    }

    public int getCallsToday(UUID userId, LocalDate today, ZoneId zone) {
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        String sql = "SELECT count(*) FROM ai_call_log WHERE user_id = ? AND called_at >= ? AND called_at < ?";
        Integer cnt = jdbcTemplate.queryForObject(sql, Integer.class, userId, Timestamp.from(start), Timestamp.from(end));
        return cnt != null ? cnt : 0;
    }

    public int getCallsRemainingToday(UUID userId) {
        ZoneId zone = factsService.getUserZoneId(userId);
        LocalDate today = LocalDate.now(zone);
        int budget = configService.get("ai_insight_daily_call_budget").intValue();
        int callsToday = getCallsToday(userId, today, zone);
        return Math.max(budget - callsToday, 0);
    }

    private synchronized void waitSpacing(int minIntervalSeconds) {
        long now = System.currentTimeMillis();
        long diff = now - lastGeminiCallTime;
        long minMs = minIntervalSeconds * 1000L;
        if (diff < minMs) {
            long sleepTime = Math.min(minMs - diff, 10000L);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        lastGeminiCallTime = System.currentTimeMillis();
    }

    public InsightResponse getInsight(UUID userId, String kind) {
        validateKind(kind);

        ZoneId zone = factsService.getUserZoneId(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate periodStart = getPeriodStart(today, kind);
        LocalDate periodEnd = getPeriodEnd(today);

        Object facts;
        boolean hasData;

        if ("spending_month".equals(kind)) {
            SpendingMonthFacts f = factsService.getSpendingMonthFacts(userId);
            facts = f;
            hasData = f.getTransactionCount() > 0;
        } else if ("fitness_week".equals(kind)) {
            FitnessWeekFacts f = factsService.getFitnessWeekFacts(userId);
            facts = f;
            hasData = f.getDaysWithData() > 0;
        } else {
            OverviewWeekFacts f = factsService.getOverviewWeekFacts(userId);
            facts = f;
            hasData = f.getTransactionCount() > 0 || f.getDaysWithFitnessData() > 0;
        }

        String canonicalJson = factsService.toCanonicalJson(facts);
        String currentHash = factsService.computeFactsHash(canonicalJson);

        AiSettingsDto settings = aiSettingsService.getSettings(userId);
        boolean aiEnabled = settings.isAiEnabled();
        boolean aiConfigured = apiKey != null && !apiKey.isBlank();

        int callsRemainingToday = getCallsRemainingToday(userId);

        // Find newest row for this user and kind
        String findSql = """
            SELECT text, created_at, model, facts_hash, prompt_version
            FROM ai_insights
            WHERE user_id = ? AND kind = ?
            ORDER BY created_at DESC
            LIMIT 1
        """;

        InsightDto insight = null;
        try {
            insight = jdbcTemplate.queryForObject(findSql, (rs, rowNum) -> {
                String text = rs.getString("text");
                Instant createdAt = rs.getTimestamp("created_at").toInstant();
                String rowModel = rs.getString("model");
                String rowHash = rs.getString("facts_hash");
                int rowPromptVersion = rs.getInt("prompt_version");

                boolean stale = !currentHash.equals(rowHash)
                        || PROMPT_VERSION != rowPromptVersion
                        || !configuredModel.equals(rowModel);

                return new InsightDto(text, createdAt, rowModel, stale);
            }, userId, kind);
        } catch (EmptyResultDataAccessException ignored) {
        }

        return new InsightResponse(
                kind,
                periodStart,
                periodEnd,
                facts,
                hasData,
                aiEnabled,
                aiConfigured,
                callsRemainingToday,
                insight
        );
    }

    public GenerateInsightResponse generateInsight(UUID userId, String kind) {
        validateKind(kind);

        ZoneId zone = factsService.getUserZoneId(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate periodStart = getPeriodStart(today, kind);
        LocalDate periodEnd = getPeriodEnd(today);

        AiSettingsDto settings = aiSettingsService.getSettings(userId);
        int callsRemaining = getCallsRemainingToday(userId);

        // Step 1: ai_enabled is false
        if (!settings.isAiEnabled()) {
            return new GenerateInsightResponse(null, "AI_DISABLED", callsRemaining);
        }

        // Step 2: API key blank
        if (apiKey == null || apiKey.isBlank()) {
            return new GenerateInsightResponse(null, "AI_NOT_CONFIGURED", callsRemaining);
        }

        // Step 3: Compute facts. hasData check
        Object facts;
        boolean hasData;

        if ("spending_month".equals(kind)) {
            SpendingMonthFacts f = factsService.getSpendingMonthFacts(userId);
            facts = f;
            hasData = f.getTransactionCount() > 0;
        } else if ("fitness_week".equals(kind)) {
            FitnessWeekFacts f = factsService.getFitnessWeekFacts(userId);
            facts = f;
            hasData = f.getDaysWithData() > 0;
        } else {
            OverviewWeekFacts f = factsService.getOverviewWeekFacts(userId);
            facts = f;
            hasData = f.getTransactionCount() > 0 || f.getDaysWithFitnessData() > 0;
        }

        if (!hasData) {
            return new GenerateInsightResponse(null, "NO_DATA", callsRemaining);
        }

        String canonicalJson = factsService.toCanonicalJson(facts);
        String factsHash = factsService.computeFactsHash(canonicalJson);

        // Step 4: Cache
        String cacheSql = """
            SELECT text, created_at, model
            FROM ai_insights
            WHERE user_id = ? AND kind = ? AND period_start = ? AND period_end = ?
              AND facts_hash = ? AND prompt_version = ? AND model = ?
            ORDER BY created_at DESC
            LIMIT 1
        """;

        try {
            InsightDto cached = jdbcTemplate.queryForObject(cacheSql, (rs, rowNum) -> new InsightDto(
                    rs.getString("text"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getString("model"),
                    false
            ), userId, kind, Date.valueOf(periodStart), Date.valueOf(periodEnd),
               factsHash, PROMPT_VERSION, configuredModel);

            if (cached != null) {
                return new GenerateInsightResponse(cached, null, callsRemaining);
            }
        } catch (EmptyResultDataAccessException ignored) {
        }

        // Step 5: Rate-limit cooldown (429 received less than 60s ago)
        long now = System.currentTimeMillis();
        if (now - lastRateLimitedTime.get() < 60000L) {
            return new GenerateInsightResponse(null, "RATE_LIMITED", callsRemaining);
        }

        // Step 6: Daily budget
        int budget = configService.get("ai_insight_daily_call_budget").intValue();
        int callsToday = getCallsToday(userId, today, zone);
        if (callsToday >= budget) {
            return new GenerateInsightResponse(null, "BUDGET_REACHED", Math.max(budget - callsToday, 0));
        }

        // Step 7: Spacing
        int minIntervalSeconds = configService.get("ai_insight_min_interval_seconds").intValue();
        waitSpacing(minIntervalSeconds);

        // Step 8: Call (attempt 1)
        String userText = "Summary type: " + kind + "\nFACTS (JSON):\n" + canonicalJson + "\nWrite the summary.";
        GeminiResult result1 = geminiClient.generate(systemInstruction, userText);

        logCall(userId, kind, result1.httpStatus, false);

        if (result1.httpStatus == 429) {
            lastRateLimitedTime.set(System.currentTimeMillis());
            return new GenerateInsightResponse(null, "RATE_LIMITED", getCallsRemainingToday(userId));
        }
        if (result1.httpStatus == 404) {
            return new GenerateInsightResponse(null, "MODEL_UNAVAILABLE", getCallsRemainingToday(userId));
        }
        if (result1.httpStatus != 200) {
            return new GenerateInsightResponse(null, "AI_ERROR", getCallsRemainingToday(userId));
        }
        if (result1.blockReason != null || (result1.finishReason != null && !"STOP".equals(result1.finishReason))
                || result1.text == null || result1.text.isBlank()) {
            return new GenerateInsightResponse(null, "AI_ERROR", getCallsRemainingToday(userId));
        }

        InsightGuard.GuardResult guard1 = InsightGuard.check(result1.text, canonicalJson);
        if (guard1.ok()) {
            updateLastLogSuccess(userId);
            InsightDto saved = saveInsightAndPrune(userId, kind, periodStart, periodEnd, factsHash, guard1.cleanText());
            return new GenerateInsightResponse(saved, null, getCallsRemainingToday(userId));
        }

        // Step 9: Retry (attempt 2)
        int callsToday2 = getCallsToday(userId, today, zone);
        if (callsToday2 >= budget) {
            return new GenerateInsightResponse(null, "BUDGET_REACHED", Math.max(budget - callsToday2, 0));
        }

        waitSpacing(minIntervalSeconds);

        String retryUserText = userText + "\nYour previous answer was rejected. Use only numbers from the FACTS, no advice, no recommendations.";
        GeminiResult result2 = geminiClient.generate(systemInstruction, retryUserText);

        logCall(userId, kind, result2.httpStatus, false);

        if (result2.httpStatus == 429) {
            lastRateLimitedTime.set(System.currentTimeMillis());
            return new GenerateInsightResponse(null, "RATE_LIMITED", getCallsRemainingToday(userId));
        }
        if (result2.httpStatus == 404) {
            return new GenerateInsightResponse(null, "MODEL_UNAVAILABLE", getCallsRemainingToday(userId));
        }
        if (result2.httpStatus != 200) {
            return new GenerateInsightResponse(null, "AI_ERROR", getCallsRemainingToday(userId));
        }
        if (result2.blockReason != null || (result2.finishReason != null && !"STOP".equals(result2.finishReason))
                || result2.text == null || result2.text.isBlank()) {
            return new GenerateInsightResponse(null, "AI_ERROR", getCallsRemainingToday(userId));
        }

        InsightGuard.GuardResult guard2 = InsightGuard.check(result2.text, canonicalJson);
        if (guard2.ok()) {
            updateLastLogSuccess(userId);
            InsightDto saved = saveInsightAndPrune(userId, kind, periodStart, periodEnd, factsHash, guard2.cleanText());
            return new GenerateInsightResponse(saved, null, getCallsRemainingToday(userId));
        }

        return new GenerateInsightResponse(null, "REJECTED_BY_GUARD", getCallsRemainingToday(userId));
    }

    private void logCall(UUID userId, String kind, int httpStatus, boolean success) {
        String sql = "INSERT INTO ai_call_log (user_id, called_at, kind, http_status, success) VALUES (?, now(), ?, ?, ?)";
        jdbcTemplate.update(sql, userId, kind, httpStatus, success);
    }

    private void updateLastLogSuccess(UUID userId) {
        String sql = """
            UPDATE ai_call_log
            SET success = true
            WHERE id = (
                SELECT id FROM ai_call_log
                WHERE user_id = ?
                ORDER BY called_at DESC
                LIMIT 1
            )
        """;
        jdbcTemplate.update(sql, userId);
    }

    @Transactional
    public InsightDto saveInsightAndPrune(
            UUID userId,
            String kind,
            LocalDate periodStart,
            LocalDate periodEnd,
            String factsHash,
            String cleanText) {

        String insertSql = """
            INSERT INTO ai_insights (user_id, kind, period_start, period_end, facts_hash, prompt_version, model, text, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (user_id, kind, period_start, period_end, facts_hash, prompt_version, model)
            DO UPDATE SET text = EXCLUDED.text, created_at = now()
        """;
        jdbcTemplate.update(insertSql, userId, kind, Date.valueOf(periodStart), Date.valueOf(periodEnd),
                factsHash, PROMPT_VERSION, configuredModel, cleanText);

        // Retention: keep only the 10 newest rows for this user and kind
        String pruneSql = """
            DELETE FROM ai_insights
            WHERE user_id = ? AND kind = ? AND id NOT IN (
                SELECT id FROM (
                    SELECT id FROM ai_insights
                    WHERE user_id = ? AND kind = ?
                    ORDER BY created_at DESC
                    LIMIT 10
                ) kept
            )
        """;
        jdbcTemplate.update(pruneSql, userId, kind, userId, kind);

        Instant now = Instant.now();
        return new InsightDto(cleanText, now, configuredModel, false);
    }
}
