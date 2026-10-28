package com.lifetracker.config;

import com.lifetracker.common.NotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

@Service
public class ConfigService {

    private final JdbcTemplate jdbcTemplate;

    public ConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BigDecimal get(String key) {
        String sql = "SELECT value FROM system_config WHERE key = ?";
        try {
            return jdbcTemplate.queryForObject(sql, BigDecimal.class, key);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("System config key not found: " + key);
        }
    }

    public void set(String key, BigDecimal value) {
        String querySql = "SELECT min_value, max_value FROM system_config WHERE key = ?";
        try {
            jdbcTemplate.queryForObject(querySql, (rs, rowNum) -> {
                BigDecimal minValue = rs.getBigDecimal("min_value");
                BigDecimal maxValue = rs.getBigDecimal("max_value");
                if (value.compareTo(minValue) < 0 || value.compareTo(maxValue) > 0) {
                    throw new IllegalArgumentException(
                            String.format("Value %s is outside allowed range [%s, %s] for key '%s'",
                                    value, minValue, maxValue, key));
                }
                return null;
            }, key);
        } catch (EmptyResultDataAccessException e) {
            throw new NotFoundException("System config key not found: " + key);
        }

        String updateSql = "UPDATE system_config SET value = ?, updated_at = ? WHERE key = ?";
        jdbcTemplate.update(updateSql, value, Timestamp.from(Instant.now()), key);
    }
}
