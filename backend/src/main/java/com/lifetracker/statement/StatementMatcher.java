package com.lifetracker.statement;

import com.lifetracker.config.ConfigService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
public class StatementMatcher {

    private final JdbcTemplate jdbcTemplate;
    private final ConfigService configService;

    public StatementMatcher(JdbcTemplate jdbcTemplate, ConfigService configService) {
        this.jdbcTemplate = jdbcTemplate;
        this.configService = configService;
    }

    public String getUserTimezone(UUID userId) {
        try {
            String tz = jdbcTemplate.queryForObject(
                    "SELECT timezone FROM user_profile WHERE user_id = ?",
                    String.class,
                    userId
            );
            if (tz != null && !tz.isBlank()) {
                return tz;
            }
        } catch (EmptyResultDataAccessException ignored) {
        }
        return "Asia/Kolkata";
    }

    public void matchRows(UUID userId, UUID accountId, String timezone, List<StatementImportRow> rows) {
        int toleranceDays = configService.get("statement_dedup_date_tolerance_days").intValue();
        Set<UUID> claimed = new HashSet<>();

        for (StatementImportRow row : rows) {
            if (row.getTxnDate() == null || row.getAmount() == null || row.getDirection() == null) {
                row.setSelected(true);
                row.setExistingTransactionId(null);
                continue;
            }

            List<UUID> candidates = findCandidates(userId, accountId, row.getAmount(), row.getDirection(), timezone, row.getTxnDate(), toleranceDays);
            UUID matched = null;
            for (UUID candidateId : candidates) {
                if (!claimed.contains(candidateId)) {
                    matched = candidateId;
                    claimed.add(candidateId);
                    break;
                }
            }

            if (matched != null) {
                row.setExistingTransactionId(matched);
                row.setSelected(false);
            } else {
                row.setExistingTransactionId(null);
                row.setSelected(true);
            }
        }
    }

    public UUID findSingleMatch(UUID userId, UUID accountId, String timezone, LocalDate date, BigDecimal amount, String direction, Set<UUID> excludeIds) {
        if (date == null || amount == null || direction == null) {
            return null;
        }
        int toleranceDays = configService.get("statement_dedup_date_tolerance_days").intValue();
        List<UUID> candidates = findCandidates(userId, accountId, amount, direction, timezone, date, toleranceDays);
        for (UUID candidateId : candidates) {
            if (excludeIds == null || !excludeIds.contains(candidateId)) {
                return candidateId;
            }
        }
        return null;
    }

    private List<UUID> findCandidates(UUID userId, UUID accountId, BigDecimal amount, String direction, String timezone, LocalDate date, int toleranceDays) {
        String sql = """
            SELECT id FROM bank_transactions
            WHERE user_id = ? AND bank_account_id = ?
              AND amount = ? AND direction = ?
              AND (transaction_date AT TIME ZONE ?)::date BETWEEN (?::date - ? * INTERVAL '1 day')::date AND (?::date + ? * INTERVAL '1 day')::date
            ORDER BY abs((transaction_date AT TIME ZONE ?)::date - ?::date), transaction_date
            LIMIT 20
            """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> (UUID) rs.getObject("id"),
                userId,
                accountId,
                amount,
                direction.toLowerCase(),
                timezone,
                date.toString(),
                toleranceDays,
                date.toString(),
                toleranceDays,
                timezone,
                date.toString()
        );
    }
}
