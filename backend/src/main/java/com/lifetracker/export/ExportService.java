package com.lifetracker.export;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ExportService {

    private final JdbcTemplate jdbcTemplate;

    public ExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportedAt", Instant.now().toString());

        // 1. Profile
        Map<String, Object> profile = null;
        try {
            profile = jdbcTemplate.queryForMap(
                    "SELECT display_name, currency_code, timezone, step_goal, water_goal_ml, calorie_goal, " +
                            "gender, height_cm, weight_kg, date_of_birth, created_at, updated_at " +
                            "FROM user_profile WHERE user_id = ?",
                    userId
            );
        } catch (EmptyResultDataAccessException ignored) {
        }
        root.put("profile", profile != null ? profile : Collections.emptyMap());

        // 2. Bank Accounts
        List<Map<String, Object>> bankAccounts = jdbcTemplate.queryForList(
                "SELECT id, account_name, account_type, account_number_last4, currency_id, current_balance, is_active, created_at, updated_at " +
                        "FROM bank_accounts WHERE user_id = ? ORDER BY created_at ASC",
                userId
        );
        root.put("bankAccounts", bankAccounts);

        // 3. Categories
        List<Map<String, Object>> categories = jdbcTemplate.queryForList(
                "SELECT id, name, type, icon, color, is_system, is_active, created_at " +
                        "FROM categories WHERE user_id = ? OR user_id IS NULL ORDER BY name ASC",
                userId
        );
        root.put("categories", categories);

        // 4. Transactions (capped at 50,000, exclude SMS fingerprint)
        Long txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bank_transactions WHERE user_id = ?",
                Long.class,
                userId
        );
        boolean isTruncated = txCount != null && txCount > 50000;
        if (isTruncated) {
            root.put("truncated", true);
        }

        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(
                "SELECT id, bank_account_id, currency_id, category_id, amount, direction, merchant_raw, " +
                        "merchant_normalized, transaction_date, source, is_self_transfer, review_status, " +
                        "review_partner_id, self_transfer_matched_id, notes, created_at, updated_at " +
                        "FROM bank_transactions WHERE user_id = ? ORDER BY transaction_date DESC LIMIT 50000",
                userId
        );
        root.put("transactions", transactions);

        // 5. Budgets
        List<Map<String, Object>> budgets = jdbcTemplate.queryForList(
                "SELECT id, category_id, month, amount_limit, is_active, created_at, updated_at " +
                        "FROM budgets WHERE user_id = ? ORDER BY month DESC",
                userId
        );
        root.put("budgets", budgets);

        // 6. Fitness Goals
        Map<String, Object> fitnessGoals = null;
        try {
            fitnessGoals = jdbcTemplate.queryForMap(
                    "SELECT daily_steps, daily_water_ml, daily_calories, weekly_workouts, updated_at " +
                            "FROM fitness_goals WHERE user_id = ?",
                    userId
            );
        } catch (EmptyResultDataAccessException ignored) {
        }
        root.put("fitnessGoals", fitnessGoals != null ? fitnessGoals : Collections.emptyMap());

        // 7. Fitness Daily
        List<Map<String, Object>> fitnessDaily = jdbcTemplate.queryForList(
                "SELECT id, day_date, steps_count, water_ml, active_calories, updated_at " +
                        "FROM fitness_daily WHERE user_id = ? ORDER BY day_date DESC",
                userId
        );
        root.put("fitnessDaily", fitnessDaily);

        // 8. Water Entries
        List<Map<String, Object>> waterEntries = jdbcTemplate.queryForList(
                "SELECT id, day_date, amount_ml, logged_at " +
                        "FROM water_entries WHERE user_id = ? ORDER BY logged_at DESC",
                userId
        );
        root.put("waterEntries", waterEntries);

        // 9. Calorie Entries
        List<Map<String, Object>> calorieEntries = jdbcTemplate.queryForList(
                "SELECT id, day_date, meal_type, food_name, calories, protein_g, carbs_g, fat_g, logged_at " +
                        "FROM calorie_entries WHERE user_id = ? ORDER BY logged_at DESC",
                userId
        );
        root.put("calorieEntries", calorieEntries);

        // 10. Workouts
        List<Map<String, Object>> workouts = jdbcTemplate.queryForList(
                "SELECT id, workout_date, workout_type, duration_minutes, calories_burned, notes, logged_at " +
                        "FROM workouts WHERE user_id = ? ORDER BY workout_date DESC",
                userId
        );
        root.put("workouts", workouts);

        // 11. Mood Log
        List<Map<String, Object>> moodLog = jdbcTemplate.queryForList(
                "SELECT id, log_date, score, energy_score, notes, created_at, updated_at " +
                        "FROM mood_log WHERE user_id = ? ORDER BY log_date DESC",
                userId
        );
        root.put("moodLog", moodLog);

        // 12. AI Settings
        Map<String, Object> aiSettings = null;
        try {
            aiSettings = jdbcTemplate.queryForMap(
                    "SELECT include_financial, include_fitness, include_mood, opt_out_all, custom_prompt, updated_at " +
                            "FROM user_ai_settings WHERE user_id = ?",
                    userId
            );
        } catch (EmptyResultDataAccessException ignored) {
        }
        root.put("aiSettings", aiSettings != null ? aiSettings : Collections.emptyMap());

        return root;
    }
}
