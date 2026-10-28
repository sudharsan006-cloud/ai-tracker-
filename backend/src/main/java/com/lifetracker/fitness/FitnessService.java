package com.lifetracker.fitness;

import com.lifetracker.common.NotFoundException;
import com.lifetracker.config.ConfigService;
import com.lifetracker.exercise.ExerciseImageUrlBuilder;
import com.lifetracker.fitness.dto.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class FitnessService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ConfigService configService;
    private final ExerciseImageUrlBuilder imageUrlBuilder;

    public FitnessService(JdbcTemplate jdbcTemplate, ConfigService configService, ExerciseImageUrlBuilder imageUrlBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.configService = configService;
        this.imageUrlBuilder = imageUrlBuilder;
    }

    public String getUserTimezone(UUID userId) {
        try {
            String tz = jdbcTemplate.queryForObject(
                    "SELECT timezone FROM user_profile WHERE user_id = ?", String.class, userId);
            if (tz != null && !tz.isBlank()) {
                ZoneId.of(tz);
                return tz;
            }
        } catch (Exception ignored) {
        }
        return "Asia/Kolkata";
    }

    public LocalDate resolveDate(String dateStr, UUID userId) {
        ZoneId zone = ZoneId.of(getUserTimezone(userId));
        LocalDate today = LocalDate.now(zone);
        if (dateStr == null || dateStr.isBlank()) {
            return today;
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format, expected yyyy-MM-dd: " + dateStr);
        }

        if (date.isAfter(today)) {
            throw new IllegalArgumentException("Date cannot be in the future: " + dateStr);
        }
        if (date.isBefore(today.minusDays(60))) {
            throw new IllegalArgumentException("Date cannot be more than 60 days ago: " + dateStr);
        }

        return date;
    }

    // -------------------------------------------------------------
    // Goals
    // -------------------------------------------------------------

    public GoalsResponse getGoals(UUID userId) {
        String sql = "SELECT steps_goal, water_goal_ml, calorie_goal FROM fitness_goals WHERE user_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                int stepsGoal = rs.getInt("steps_goal");
                int waterGoalMl = rs.getInt("water_goal_ml");
                Integer calorieGoal = (Integer) rs.getObject("calorie_goal");
                return new GoalsResponse(stepsGoal, waterGoalMl, calorieGoal);
            }, userId);
        } catch (EmptyResultDataAccessException e) {
            int defaultSteps = configService.get("default_steps_goal").intValue();
            int defaultWater = configService.get("default_water_goal_ml").intValue();
            return new GoalsResponse(defaultSteps, defaultWater, null);
        }
    }

    public GoalsResponse putGoals(UUID userId, GoalsRequest request) {
        if (request.getStepsGoal() == null || request.getStepsGoal() < 100 || request.getStepsGoal() > 100000) {
            throw new IllegalArgumentException("stepsGoal must be between 100 and 100000");
        }
        if (request.getWaterGoalMl() == null || request.getWaterGoalMl() < 250 || request.getWaterGoalMl() > 10000) {
            throw new IllegalArgumentException("waterGoalMl must be between 250 and 10000");
        }
        if (request.getCalorieGoal() != null && (request.getCalorieGoal() < 500 || request.getCalorieGoal() > 10000)) {
            throw new IllegalArgumentException("calorieGoal must be null or between 500 and 10000");
        }

        String sql = """
            INSERT INTO fitness_goals (user_id, steps_goal, water_goal_ml, calorie_goal, updated_at)
            VALUES (?, ?, ?, ?, now())
            ON CONFLICT (user_id) DO UPDATE
            SET steps_goal = EXCLUDED.steps_goal,
                water_goal_ml = EXCLUDED.water_goal_ml,
                calorie_goal = EXCLUDED.calorie_goal,
                updated_at = now()
        """;

        jdbcTemplate.update(sql, userId, request.getStepsGoal(), request.getWaterGoalMl(), request.getCalorieGoal());
        return new GoalsResponse(request.getStepsGoal(), request.getWaterGoalMl(), request.getCalorieGoal());
    }

    // -------------------------------------------------------------
    // Steps
    // -------------------------------------------------------------

    @Transactional
    public StepsResponse updateSteps(UUID userId, StepsRequest request) {
        if (request.getSource() == null || (!request.getSource().equals("sensor") && !request.getSource().equals("manual"))) {
            throw new IllegalArgumentException("steps source must be 'sensor' or 'manual'");
        }
        if (request.getSteps() == null || request.getSteps() < 0 || request.getSteps() > 200000) {
            throw new IllegalArgumentException("steps must be between 0 and 200000");
        }

        LocalDate logDate = resolveDate(request.getDate(), userId);
        String selectSql = "SELECT steps, steps_source FROM fitness_daily_log WHERE user_id = ? AND log_date = ?";

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(selectSql, userId, logDate);

        if (existing.isEmpty()) {
            String insertSql = """
                INSERT INTO fitness_daily_log (user_id, log_date, steps, steps_source, updated_at)
                VALUES (?, ?, ?, ?, now())
            """;
            jdbcTemplate.update(insertSql, userId, logDate, request.getSteps(), request.getSource());
            return new StepsResponse(logDate.toString(), request.getSteps(), request.getSource());
        }

        Map<String, Object> row = existing.get(0);
        int storedSteps = ((Number) row.get("steps")).intValue();
        String storedSource = (String) row.get("steps_source");

        boolean shouldChange = true;
        if ("manual".equals(storedSource) && "sensor".equals(request.getSource())) {
            shouldChange = false;
        } else if ("sensor".equals(storedSource) && "sensor".equals(request.getSource()) && request.getSteps() < storedSteps) {
            shouldChange = false;
        }

        if (shouldChange) {
            String updateSql = """
                UPDATE fitness_daily_log
                SET steps = ?, steps_source = ?, updated_at = now()
                WHERE user_id = ? AND log_date = ?
            """;
            jdbcTemplate.update(updateSql, request.getSteps(), request.getSource(), userId, logDate);
            return new StepsResponse(logDate.toString(), request.getSteps(), request.getSource());
        } else {
            return new StepsResponse(logDate.toString(), storedSteps, storedSource);
        }
    }

    // -------------------------------------------------------------
    // Water
    // -------------------------------------------------------------

    public WaterEntryResponse addWater(UUID userId, WaterRequest request) {
        if (request.getAmountMl() == null || request.getAmountMl() < 1 || request.getAmountMl() > 5000) {
            throw new IllegalArgumentException("amountMl must be between 1 and 5000");
        }

        LocalDate logDate = resolveDate(request.getDate(), userId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        String sql = """
            INSERT INTO water_entries (id, user_id, log_date, amount_ml, logged_at)
            VALUES (?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql, id, userId, logDate, request.getAmountMl(), Timestamp.from(now));

        return new WaterEntryResponse(id, logDate.toString(), request.getAmountMl(), now);
    }

    public List<WaterEntryResponse> getWaterEntries(UUID userId, String dateStr) {
        LocalDate logDate = resolveDate(dateStr, userId);
        String sql = """
            SELECT id, log_date, amount_ml, logged_at
            FROM water_entries
            WHERE user_id = ? AND log_date = ?
            ORDER BY logged_at DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new WaterEntryResponse(
                (UUID) rs.getObject("id"),
                rs.getDate("log_date").toLocalDate().toString(),
                rs.getInt("amount_ml"),
                rs.getTimestamp("logged_at").toInstant()
        ), userId, logDate);
    }

    public void deleteWaterEntry(UUID userId, UUID id) {
        int affected = jdbcTemplate.update(
                "DELETE FROM water_entries WHERE id = ? AND user_id = ?", id, userId);
        if (affected == 0) {
            throw new NotFoundException("Water entry not found: " + id);
        }
    }

    // -------------------------------------------------------------
    // Calories
    // -------------------------------------------------------------

    public CalorieEntryResponse addCalorie(UUID userId, CalorieRequest request) {
        if (request.getKcal() == null || request.getKcal() < 1 || request.getKcal() > 10000) {
            throw new IllegalArgumentException("kcal must be between 1 and 10000");
        }
        if (request.getLabel() != null && request.getLabel().length() > 80) {
            throw new IllegalArgumentException("label must be at most 80 characters");
        }

        LocalDate logDate = resolveDate(request.getDate(), userId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        String sql = """
            INSERT INTO calorie_entries (id, user_id, log_date, kcal, label, logged_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql, id, userId, logDate, request.getKcal(), request.getLabel(), Timestamp.from(now));

        return new CalorieEntryResponse(id, logDate.toString(), request.getKcal(), request.getLabel(), now);
    }

    public List<CalorieEntryResponse> getCalorieEntries(UUID userId, String dateStr) {
        LocalDate logDate = resolveDate(dateStr, userId);
        String sql = """
            SELECT id, log_date, kcal, label, logged_at
            FROM calorie_entries
            WHERE user_id = ? AND log_date = ?
            ORDER BY logged_at DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CalorieEntryResponse(
                (UUID) rs.getObject("id"),
                rs.getDate("log_date").toLocalDate().toString(),
                rs.getInt("kcal"),
                rs.getString("label"),
                rs.getTimestamp("logged_at").toInstant()
        ), userId, logDate);
    }

    public void deleteCalorieEntry(UUID userId, UUID id) {
        int affected = jdbcTemplate.update(
                "DELETE FROM calorie_entries WHERE id = ? AND user_id = ?", id, userId);
        if (affected == 0) {
            throw new NotFoundException("Calorie entry not found: " + id);
        }
    }

    // -------------------------------------------------------------
    // Daily Summary and Range
    // -------------------------------------------------------------

    public DailySummaryResponse getDailySummary(UUID userId, String dateStr) {
        LocalDate date = resolveDate(dateStr, userId);
        List<RangeDayData> list = executeRangeQuery(userId, date, date);
        RangeDayData dayData = list.isEmpty() ? new RangeDayData(date, 0, "none", 0, 0, 0, 0) : list.get(0);

        GoalsResponse goals = getGoals(userId);

        int stepsPercent = (int) Math.floor((double) dayData.steps * 100.0 / goals.getStepsGoal());
        int waterPercent = (int) Math.floor((double) dayData.waterMl * 100.0 / goals.getWaterGoalMl());
        Integer caloriesPercent = null;
        if (goals.getCalorieGoal() != null) {
            caloriesPercent = (int) Math.floor((double) dayData.caloriesIn * 100.0 / goals.getCalorieGoal());
        }

        Integer bmr = calculateBmrForUser(userId, date);

        return new DailySummaryResponse(
                date.toString(),
                dayData.steps,
                dayData.stepsSource,
                goals.getStepsGoal(),
                stepsPercent,
                dayData.waterMl,
                goals.getWaterGoalMl(),
                waterPercent,
                dayData.caloriesIn,
                goals.getCalorieGoal(),
                caloriesPercent,
                dayData.caloriesBurnedEst,
                dayData.workoutCount,
                bmr
        );
    }

    public RangeResponse getRange(UUID userId, String fromStr, String toStr) {
        if (fromStr == null || fromStr.isBlank() || toStr == null || toStr.isBlank()) {
            throw new IllegalArgumentException("Both 'from' and 'to' query parameters are required");
        }

        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(fromStr.trim());
            to = LocalDate.parse(toStr.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format, expected yyyy-MM-dd");
        }

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' date must not be before 'from' date");
        }

        ZoneId zone = ZoneId.of(getUserTimezone(userId));
        LocalDate today = LocalDate.now(zone);
        if (to.isAfter(today)) {
            throw new IllegalArgumentException("'to' date must not be after today: " + toStr);
        }

        long daysBetween = ChronoUnit.DAYS.between(from, to) + 1;
        if (daysBetween > 62) {
            throw new IllegalArgumentException("Range cannot exceed 62 days");
        }

        List<RangeDayData> rawDays = executeRangeQuery(userId, from, to);
        List<RangeDayResponse> days = new ArrayList<>();

        long totalSteps = 0;
        long totalWater = 0;
        long totalCaloriesIn = 0;

        for (RangeDayData d : rawDays) {
            days.add(new RangeDayResponse(
                    d.date.toString(),
                    d.steps,
                    d.waterMl,
                    d.caloriesIn,
                    d.caloriesBurnedEst,
                    d.workoutCount
            ));
            totalSteps += d.steps;
            totalWater += d.waterMl;
            totalCaloriesIn += d.caloriesIn;
        }

        BigDecimal numDays = BigDecimal.valueOf(days.size());
        int avgSteps = numDays.compareTo(BigDecimal.ZERO) > 0 ?
                BigDecimal.valueOf(totalSteps).divide(numDays, 0, RoundingMode.HALF_UP).intValue() : 0;
        int avgWater = numDays.compareTo(BigDecimal.ZERO) > 0 ?
                BigDecimal.valueOf(totalWater).divide(numDays, 0, RoundingMode.HALF_UP).intValue() : 0;
        int avgCaloriesIn = numDays.compareTo(BigDecimal.ZERO) > 0 ?
                BigDecimal.valueOf(totalCaloriesIn).divide(numDays, 0, RoundingMode.HALF_UP).intValue() : 0;

        return new RangeResponse(days, new RangeAveragesResponse(avgSteps, avgWater, avgCaloriesIn));
    }

    private static class RangeDayData {
        LocalDate date;
        int steps;
        String stepsSource;
        int waterMl;
        int caloriesIn;
        int caloriesBurnedEst;
        int workoutCount;

        RangeDayData(LocalDate date, int steps, String stepsSource, int waterMl, int caloriesIn, int caloriesBurnedEst, int workoutCount) {
            this.date = date;
            this.steps = steps;
            this.stepsSource = stepsSource;
            this.waterMl = waterMl;
            this.caloriesIn = caloriesIn;
            this.caloriesBurnedEst = caloriesBurnedEst;
            this.workoutCount = workoutCount;
        }
    }

    private List<RangeDayData> executeRangeQuery(UUID userId, LocalDate from, LocalDate to) {
        String sql = """
            SELECT d::date AS day,
              COALESCE(f.steps, 0) AS steps,
              COALESCE(f.steps_source, 'none') AS steps_source,
              COALESCE((SELECT SUM(w.amount_ml) FROM water_entries w
                         WHERE w.user_id = :userId AND w.log_date = d::date), 0) AS water_ml,
              COALESCE((SELECT SUM(c.kcal) FROM calorie_entries c
                         WHERE c.user_id = :userId AND c.log_date = d::date), 0) AS calories_in,
              COALESCE((SELECT SUM(k.calories_burned_est) FROM workouts k
                         WHERE k.user_id = :userId AND k.workout_date = d::date), 0) AS calories_burned_est,
              (SELECT COUNT(*) FROM workouts k
                WHERE k.user_id = :userId AND k.workout_date = d::date) AS workout_count
            FROM generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') AS d
            LEFT JOIN fitness_daily_log f ON f.user_id = :userId AND f.log_date = d::date
            ORDER BY day
        """;

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("userId", userId);
        params.addValue("from", from.toString());
        params.addValue("to", to.toString());

        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> new RangeDayData(
                rs.getDate("day").toLocalDate(),
                rs.getInt("steps"),
                rs.getString("steps_source"),
                rs.getInt("water_ml"),
                rs.getInt("calories_in"),
                rs.getInt("calories_burned_est"),
                rs.getInt("workout_count")
        ));
    }

    private Integer calculateBmrForUser(UUID userId, LocalDate date) {
        String sql = "SELECT sex_at_birth, weight_kg, height_cm, date_of_birth FROM user_profile WHERE user_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                String sex = rs.getString("sex_at_birth");
                BigDecimal weight = rs.getBigDecimal("weight_kg");
                BigDecimal height = rs.getBigDecimal("height_cm");
                Date dobDate = rs.getDate("date_of_birth");
                LocalDate dob = dobDate != null ? dobDate.toLocalDate() : null;
                return BmrCalculator.calculate(sex, weight, height, dob, date);
            }, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // -------------------------------------------------------------
    // Workouts
    // -------------------------------------------------------------

    private BigDecimal getMetForIntensity(String intensity) {
        return switch (intensity) {
            case "light" -> configService.get("workout_met_light");
            case "moderate" -> configService.get("workout_met_moderate");
            case "vigorous" -> configService.get("workout_met_vigorous");
            default -> throw new IllegalArgumentException("Unknown intensity: " + intensity);
        };
    }

    private BigDecimal getUserWeight(UUID userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT weight_kg FROM user_profile WHERE user_id = ?", BigDecimal.class, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Transactional
    public WorkoutResponse createWorkout(UUID userId, WorkoutCreateRequest request) {
        validateWorkoutRequest(request);

        LocalDate workoutDate = resolveDate(request.getDate(), userId);
        BigDecimal met = getMetForIntensity(request.getIntensity());
        BigDecimal weight = getUserWeight(userId);
        Integer caloriesBurnedEst = WorkoutCalorieCalculator.calculate(met, weight, request.getDurationMin());

        UUID workoutId = UUID.randomUUID();
        Instant now = Instant.now();

        String insertWorkoutSql = """
            INSERT INTO workouts (id, user_id, workout_date, name, duration_min, intensity, calories_burned_est, notes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(insertWorkoutSql, workoutId, userId, workoutDate, request.getName().trim(),
                request.getDurationMin(), request.getIntensity(), caloriesBurnedEst,
                request.getNotes() != null ? request.getNotes().trim() : null, Timestamp.from(now));

        insertWorkoutEntries(workoutId, request.getEntries());

        return getWorkout(userId, workoutId);
    }

    public WorkoutResponse getWorkout(UUID userId, UUID workoutId) {
        String sql = """
            SELECT id, workout_date, name, duration_min, intensity, calories_burned_est, notes
            FROM workouts
            WHERE id = ? AND user_id = ?
        """;

        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                UUID id = (UUID) rs.getObject("id");
                String date = rs.getDate("workout_date").toLocalDate().toString();
                String name = rs.getString("name");
                int duration = rs.getInt("duration_min");
                String intensity = rs.getString("intensity");
                Integer caloriesBurned = (Integer) rs.getObject("calories_burned_est");
                String notes = rs.getString("notes");

                List<WorkoutEntryResponse> entries = getWorkoutEntries(id);

                return new WorkoutResponse(id, date, name, duration, intensity, caloriesBurned, notes, entries);
            }, workoutId, userId);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("Workout not found: " + workoutId);
        }
    }

    public WorkoutPageResponse getWorkouts(UUID userId, String fromStr, String toStr, int page, int size) {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;

        StringBuilder whereClause = new StringBuilder(" WHERE w.user_id = :userId ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("userId", userId);

        if (fromStr != null && !fromStr.isBlank()) {
            whereClause.append(" AND w.workout_date >= CAST(:from AS date) ");
            params.addValue("from", fromStr.trim());
        }
        if (toStr != null && !toStr.isBlank()) {
            whereClause.append(" AND w.workout_date <= CAST(:to AS date) ");
            params.addValue("to", toStr.trim());
        }

        String countSql = "SELECT COUNT(*) FROM workouts w " + whereClause;
        Long total = namedParameterJdbcTemplate.queryForObject(countSql, params, Long.class);
        if (total == null) total = 0L;

        String selectSql = """
            SELECT w.id, w.workout_date, w.name, w.duration_min, w.intensity, w.calories_burned_est, w.notes
            FROM workouts w
        """ + whereClause + " ORDER BY w.workout_date DESC, w.created_at DESC LIMIT :limit OFFSET :offset ";

        params.addValue("limit", size);
        params.addValue("offset", page * size);

        List<WorkoutResponse> items = namedParameterJdbcTemplate.query(selectSql, params, (rs, rowNum) -> {
            UUID id = (UUID) rs.getObject("id");
            String date = rs.getDate("workout_date").toLocalDate().toString();
            String name = rs.getString("name");
            int duration = rs.getInt("duration_min");
            String intensity = rs.getString("intensity");
            Integer caloriesBurned = (Integer) rs.getObject("calories_burned_est");
            String notes = rs.getString("notes");

            List<WorkoutEntryResponse> entries = getWorkoutEntries(id);

            return new WorkoutResponse(id, date, name, duration, intensity, caloriesBurned, notes, entries);
        });

        return new WorkoutPageResponse(items, page, size, total);
    }

    @Transactional
    public WorkoutResponse updateWorkout(UUID userId, UUID workoutId, WorkoutCreateRequest request) {
        // Check ownership
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM workouts WHERE id = ? AND user_id = ?", Integer.class, workoutId, userId);
        if (exists == null || exists == 0) {
            throw new NotFoundException("Workout not found: " + workoutId);
        }

        validateWorkoutRequest(request);

        LocalDate workoutDate = resolveDate(request.getDate(), userId);
        BigDecimal met = getMetForIntensity(request.getIntensity());
        BigDecimal weight = getUserWeight(userId);
        Integer caloriesBurnedEst = WorkoutCalorieCalculator.calculate(met, weight, request.getDurationMin());

        String updateSql = """
            UPDATE workouts
            SET workout_date = ?, name = ?, duration_min = ?, intensity = ?, calories_burned_est = ?, notes = ?
            WHERE id = ? AND user_id = ?
        """;
        jdbcTemplate.update(updateSql, workoutDate, request.getName().trim(), request.getDurationMin(),
                request.getIntensity(), caloriesBurnedEst,
                request.getNotes() != null ? request.getNotes().trim() : null, workoutId, userId);

        jdbcTemplate.update("DELETE FROM workout_entries WHERE workout_id = ?", workoutId);
        insertWorkoutEntries(workoutId, request.getEntries());

        return getWorkout(userId, workoutId);
    }

    @Transactional
    public void deleteWorkout(UUID userId, UUID workoutId) {
        int affected = jdbcTemplate.update(
                "DELETE FROM workouts WHERE id = ? AND user_id = ?", workoutId, userId);
        if (affected == 0) {
            throw new NotFoundException("Workout not found: " + workoutId);
        }
    }

    private void validateWorkoutRequest(WorkoutCreateRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty() || request.getName().trim().length() > 80) {
            throw new IllegalArgumentException("Workout name must be 1 to 80 characters");
        }
        if (request.getDurationMin() == null || request.getDurationMin() < 1 || request.getDurationMin() > 600) {
            throw new IllegalArgumentException("Workout durationMin must be between 1 and 600");
        }
        if (request.getIntensity() == null || (!request.getIntensity().equals("light")
                && !request.getIntensity().equals("moderate") && !request.getIntensity().equals("vigorous"))) {
            throw new IllegalArgumentException("Workout intensity must be light, moderate, or vigorous");
        }
        if (request.getNotes() != null && request.getNotes().length() > 500) {
            throw new IllegalArgumentException("Workout notes must be at most 500 characters");
        }
        if (request.getEntries() == null || request.getEntries().isEmpty() || request.getEntries().size() > 40) {
            throw new IllegalArgumentException("Workout entries must have 1 to 40 items");
        }

        for (int i = 0; i < request.getEntries().size(); i++) {
            WorkoutEntryRequest entry = request.getEntries().get(i);
            boolean hasExerciseId = entry.getExerciseId() != null;
            boolean hasCustomName = entry.getCustomName() != null && !entry.getCustomName().trim().isEmpty();

            if ((hasExerciseId && hasCustomName) || (!hasExerciseId && !hasCustomName)) {
                throw new IllegalArgumentException("Workout entry " + i + " must have EXACTLY ONE of exerciseId or customName");
            }
            if (hasCustomName && entry.getCustomName().trim().length() > 80) {
                throw new IllegalArgumentException("Workout entry " + i + " customName must be 1 to 80 characters");
            }
            if (hasExerciseId) {
                Integer exExists = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM exercises WHERE id = ?", Integer.class, entry.getExerciseId());
                if (exExists == null || exExists == 0) {
                    throw new IllegalArgumentException("Workout entry " + i + " exerciseId does not exist: " + entry.getExerciseId());
                }
            }
            if (entry.getSets() == null || entry.getSets() < 1 || entry.getSets() > 50) {
                throw new IllegalArgumentException("Workout entry " + i + " sets must be between 1 and 50");
            }
            boolean hasReps = entry.getReps() != null;
            boolean hasDuration = entry.getDurationSeconds() != null;
            if (!hasReps && !hasDuration) {
                throw new IllegalArgumentException("Workout entry " + i + " must have at least one of reps or durationSeconds");
            }
            if (hasReps && (entry.getReps() < 1 || entry.getReps() > 500)) {
                throw new IllegalArgumentException("Workout entry " + i + " reps must be between 1 and 500");
            }
            if (hasDuration && (entry.getDurationSeconds() < 1 || entry.getDurationSeconds() > 7200)) {
                throw new IllegalArgumentException("Workout entry " + i + " durationSeconds must be between 1 and 7200");
            }
            if (entry.getWeightKg() != null && (entry.getWeightKg().compareTo(BigDecimal.ZERO) < 0
                    || entry.getWeightKg().compareTo(BigDecimal.valueOf(1000)) > 0)) {
                throw new IllegalArgumentException("Workout entry " + i + " weightKg must be between 0 and 1000");
            }
        }
    }

    private void insertWorkoutEntries(UUID workoutId, List<WorkoutEntryRequest> entries) {
        String sql = """
            INSERT INTO workout_entries (
                workout_id, position, exercise_id, custom_name, sets, reps, duration_seconds, weight_kg
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        for (int i = 0; i < entries.size(); i++) {
            WorkoutEntryRequest e = entries.get(i);
            jdbcTemplate.update(sql,
                    workoutId,
                    i,
                    e.getExerciseId(),
                    e.getCustomName() != null ? e.getCustomName().trim() : null,
                    e.getSets(),
                    e.getReps(),
                    e.getDurationSeconds(),
                    e.getWeightKg()
            );
        }
    }

    private List<WorkoutEntryResponse> getWorkoutEntries(UUID workoutId) {
        String sql = """
            SELECT we.id, we.position, we.exercise_id, we.custom_name,
                   we.sets, we.reps, we.duration_seconds, we.weight_kg,
                   ex.name AS exercise_lib_name, ex.images AS exercise_images
            FROM workout_entries we
            LEFT JOIN exercises ex ON ex.id = we.exercise_id
            WHERE we.workout_id = ?
            ORDER BY we.position ASC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            UUID id = (UUID) rs.getObject("id");
            int position = rs.getInt("position");
            UUID exerciseId = (UUID) rs.getObject("exercise_id");
            String customName = rs.getString("custom_name");
            String exerciseLibName = rs.getString("exercise_lib_name");
            String exerciseName = exerciseLibName != null ? exerciseLibName : customName;

            Array imgArray = rs.getArray("exercise_images");
            List<String> images = toList(imgArray);
            String thumbnailUrl = imageUrlBuilder.thumbnailUrl(images);

            int sets = rs.getInt("sets");
            Integer reps = (Integer) rs.getObject("reps");
            Integer durationSeconds = (Integer) rs.getObject("duration_seconds");
            BigDecimal weightKg = rs.getBigDecimal("weight_kg");

            return new WorkoutEntryResponse(id, position, exerciseId, exerciseName, thumbnailUrl, sets, reps, durationSeconds, weightKg);
        }, workoutId);
    }

    private List<String> toList(Array array) {
        if (array == null) {
            return List.of();
        }
        try {
            Object obj = array.getArray();
            if (obj instanceof String[] arr) {
                return Arrays.asList(arr);
            } else if (obj instanceof Object[] arr) {
                List<String> list = new ArrayList<>();
                for (Object o : arr) {
                    if (o != null) list.add(o.toString());
                }
                return list;
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }
}
