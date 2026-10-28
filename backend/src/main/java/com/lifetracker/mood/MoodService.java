package com.lifetracker.mood;

import com.lifetracker.common.NotFoundException;
import com.lifetracker.config.ConfigService;
import com.lifetracker.mood.dto.MoodNudgeResponse;
import com.lifetracker.mood.dto.MoodRequest;
import com.lifetracker.mood.dto.MoodResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class MoodService {

    private final JdbcTemplate jdbcTemplate;
    private final ConfigService configService;

    public MoodService(JdbcTemplate jdbcTemplate, ConfigService configService) {
        this.jdbcTemplate = jdbcTemplate;
        this.configService = configService;
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

    @Transactional
    public MoodResponse putMood(UUID userId, MoodRequest req) {
        if (req.getScore() == null || req.getScore() < 1 || req.getScore() > 5) {
            throw new IllegalArgumentException("Score must be between 1 and 5");
        }
        if (req.getNote() != null && req.getNote().length() > 300) {
            throw new IllegalArgumentException("Note must be at most 300 characters");
        }

        LocalDate date = resolveDate(req.getDate(), userId);
        String note = req.getNote() != null && !req.getNote().trim().isEmpty() ? req.getNote().trim() : null;

        String sql = """
            INSERT INTO mood_log (user_id, log_date, score, note, updated_at)
            VALUES (?, ?, ?, ?, now())
            ON CONFLICT (user_id, log_date) DO UPDATE
            SET score = EXCLUDED.score,
                note = EXCLUDED.note,
                updated_at = now()
        """;

        jdbcTemplate.update(sql, userId, Date.valueOf(date), req.getScore(), note);
        return new MoodResponse(date.toString(), req.getScore(), note);
    }

    public List<MoodResponse> getMoodEntries(UUID userId, String fromStr, String toStr) {
        ZoneId zone = ZoneId.of(getUserTimezone(userId));
        LocalDate today = LocalDate.now(zone);

        LocalDate from;
        LocalDate to;

        if ((fromStr == null || fromStr.isBlank()) && (toStr == null || toStr.isBlank())) {
            from = today.minusDays(13);
            to = today;
        } else if (fromStr != null && !fromStr.isBlank() && toStr != null && !toStr.isBlank()) {
            try {
                from = LocalDate.parse(fromStr.trim());
                to = LocalDate.parse(toStr.trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid date format, expected yyyy-MM-dd");
            }
            if (to.isBefore(from)) {
                throw new IllegalArgumentException("'to' date must not be before 'from' date");
            }
            if (ChronoUnit.DAYS.between(from, to) > 90) {
                throw new IllegalArgumentException("Date range cannot exceed 90 days");
            }
        } else {
            throw new IllegalArgumentException("Both 'from' and 'to' must be provided or omitted together");
        }

        String sql = """
            SELECT log_date, score, note
            FROM mood_log
            WHERE user_id = ? AND log_date >= ? AND log_date <= ?
            ORDER BY log_date DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new MoodResponse(
                rs.getDate("log_date").toLocalDate().toString(),
                rs.getShort("score"),
                rs.getString("note")
        ), userId, Date.valueOf(from), Date.valueOf(to));
    }

    @Transactional
    public void deleteMood(UUID userId, String dateStr) {
        LocalDate date = resolveDate(dateStr, userId);
        int rows = jdbcTemplate.update(
                "DELETE FROM mood_log WHERE user_id = ? AND log_date = ?", userId, Date.valueOf(date));
        if (rows == 0) {
            throw new NotFoundException("Mood entry not found for date: " + dateStr);
        }
    }

    public MoodNudgeResponse getNudge(UUID userId) {
        int window = configService.get("mood_nudge_window_days").intValue();
        short lowMax = configService.get("mood_nudge_low_score_max").shortValue();
        int minLow = configService.get("mood_nudge_min_low_days").intValue();
        int cooldown = configService.get("mood_nudge_cooldown_days").intValue();

        ZoneId zone = ZoneId.of(getUserTimezone(userId));
        LocalDate today = LocalDate.now(zone);
        LocalDate windowStart = today.minusDays(window - 1);

        Integer lowDays = jdbcTemplate.queryForObject("""
            SELECT count(*)
            FROM mood_log
            WHERE user_id = ? AND log_date >= ? AND log_date <= ? AND score <= ?
        """, Integer.class, userId, Date.valueOf(windowStart), Date.valueOf(today), lowMax);

        if (lowDays == null) {
            lowDays = 0;
        }

        LocalDate dismissedOn = null;
        try {
            Date dismissedDate = jdbcTemplate.queryForObject(
                    "SELECT mood_nudge_dismissed_on FROM user_ai_settings WHERE user_id = ?",
                    Date.class, userId);
            if (dismissedDate != null) {
                dismissedOn = dismissedDate.toLocalDate();
            }
        } catch (EmptyResultDataAccessException ignored) {
        }

        if (dismissedOn != null && ChronoUnit.DAYS.between(dismissedOn, today) < cooldown) {
            return new MoodNudgeResponse(false, null);
        }

        if (lowDays >= minLow) {
            String message = "Your mood entries were low on " + lowDays + " of the last " + window +
                    " days. This app cannot tell you why. If it is weighing on you, talking with someone you trust or a health professional can help.";
            return new MoodNudgeResponse(true, message);
        }

        return new MoodNudgeResponse(false, null);
    }

    @Transactional
    public void dismissNudge(UUID userId) {
        ZoneId zone = ZoneId.of(getUserTimezone(userId));
        LocalDate today = LocalDate.now(zone);

        String sql = """
            INSERT INTO user_ai_settings (user_id, mood_nudge_dismissed_on, updated_at)
            VALUES (?, ?, now())
            ON CONFLICT (user_id) DO UPDATE
            SET mood_nudge_dismissed_on = EXCLUDED.mood_nudge_dismissed_on,
                updated_at = now()
        """;
        jdbcTemplate.update(sql, userId, Date.valueOf(today));
    }
}
