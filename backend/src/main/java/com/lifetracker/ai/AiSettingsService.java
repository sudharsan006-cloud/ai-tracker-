package com.lifetracker.ai;

import com.lifetracker.ai.dto.AiSettingsDto;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AiSettingsService {

    private final JdbcTemplate jdbcTemplate;

    public AiSettingsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AiSettingsDto getSettings(UUID userId) {
        String sql = "SELECT ai_enabled, include_mood FROM user_ai_settings WHERE user_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new AiSettingsDto(
                    rs.getBoolean("ai_enabled"),
                    rs.getBoolean("include_mood")
            ), userId);
        } catch (EmptyResultDataAccessException e) {
            return new AiSettingsDto(true, false);
        }
    }

    @Transactional
    public AiSettingsDto updateSettings(UUID userId, AiSettingsDto dto) {
        String sql = """
            INSERT INTO user_ai_settings (user_id, ai_enabled, include_mood, updated_at)
            VALUES (?, ?, ?, now())
            ON CONFLICT (user_id) DO UPDATE
            SET ai_enabled = EXCLUDED.ai_enabled,
                include_mood = EXCLUDED.include_mood,
                updated_at = now()
        """;
        jdbcTemplate.update(sql, userId, dto.isAiEnabled(), dto.isIncludeMood());
        return new AiSettingsDto(dto.isAiEnabled(), dto.isIncludeMood());
    }
}
