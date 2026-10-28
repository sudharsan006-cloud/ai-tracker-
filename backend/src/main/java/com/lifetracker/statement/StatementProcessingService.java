package com.lifetracker.statement;

import com.lifetracker.config.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StatementProcessingService {

    private static final Logger log = LoggerFactory.getLogger(StatementProcessingService.class);

    private final StatementImportRepository importRepository;
    private final StatementImportRowRepository rowRepository;
    private final StatementParserRegistry parserRegistry;
    private final TextExtractor textExtractor;
    private final StatementMatcher statementMatcher;
    private final ConfigService configService;
    private final JdbcTemplate jdbcTemplate;
    private final int jobDeadlineMinutes;

    public StatementProcessingService(
            StatementImportRepository importRepository,
            StatementImportRowRepository rowRepository,
            StatementParserRegistry parserRegistry,
            TextExtractor textExtractor,
            StatementMatcher statementMatcher,
            ConfigService configService,
            JdbcTemplate jdbcTemplate,
            @Value("${app.statement.job-deadline-minutes:6}") int jobDeadlineMinutes) {
        this.importRepository = importRepository;
        this.rowRepository = rowRepository;
        this.parserRegistry = parserRegistry;
        this.textExtractor = textExtractor;
        this.statementMatcher = statementMatcher;
        this.configService = configService;
        this.jdbcTemplate = jdbcTemplate;
        this.jobDeadlineMinutes = jobDeadlineMinutes;
    }

    @Async("statementExecutor")
    public void process(UUID importId, String tempFilePath, String password) {
        File tempFile = new File(tempFilePath);
        Instant jobStartTime = Instant.now();
        Duration jobDeadline = Duration.ofMinutes(jobDeadlineMinutes);

        try {
            StatementImport imp = importRepository.findById(importId).orElse(null);
            if (imp == null) {
                return;
            }

            // Step 1: Look up bank short code and find parser
            String bankShortCode = getBankShortCode(imp.getBankAccountId());
            Optional<BankStatementParser> parserOpt = parserRegistry.find(bankShortCode);
            if (parserOpt.isEmpty()) {
                failImport(imp, "NO_PARSER");
                return;
            }
            BankStatementParser parser = parserOpt.get();

            // Step 2: Extract text / OCR lines
            ExtractedText extracted;
            try {
                extracted = textExtractor.extract(tempFile, imp.getFileType(), password, jobStartTime, jobDeadline);
            } catch (StatementException se) {
                failImport(imp, se.getErrorCode());
                return;
            }

            // Step 3: Parse extracted lines
            ParsedStatement parsedStatement;
            try {
                parsedStatement = parser.parse(extracted.getLines());
            } catch (Exception e) {
                log.warn("Parser error for import id: {}, error: {}", importId, e.getClass().getSimpleName());
                failImport(imp, "UNREADABLE_FILE");
                return;
            }

            if (parsedStatement == null || parsedStatement.rows.isEmpty()) {
                failImport(imp, "NO_ROWS_FOUND");
                return;
            }

            // Step 4: Build rows, apply flags and balance check
            BigDecimal minConfVal = configService.get("statement_ocr_min_confidence");
            double minConfidence = minConfVal != null ? minConfVal.doubleValue() : 80.0;

            BalanceChecker.BalanceCheckResult balanceResult = BalanceChecker.check(
                    parsedStatement.openingBalance,
                    parsedStatement.closingBalance,
                    parsedStatement.rows
            );

            List<StatementImportRow> stagingRows = new ArrayList<>();
            for (int i = 0; i < parsedStatement.rows.size(); i++) {
                ParsedRow pr = parsedStatement.rows.get(i);
                StatementImportRow row = new StatementImportRow();
                row.setImportId(importId);
                row.setRowIndex(i);
                row.setTxnDate(pr.date);
                row.setDescription(pr.description != null && pr.description.length() > 255 ? pr.description.substring(0, 255) : pr.description);
                row.setAmount(pr.amount);
                row.setDirection(pr.direction != null ? pr.direction.toLowerCase() : null);
                row.setBalanceAfter(pr.balanceAfter);
                row.setConfidence(pr.confidence);

                // Flag assignment priority
                if (pr.parseError) {
                    row.setNeedsCheck(true);
                    row.setCheckReason("parse_error");
                } else if (pr.date == null || pr.amount == null || pr.direction == null) {
                    row.setNeedsCheck(true);
                    row.setCheckReason("missing_fields");
                } else if (balanceResult.getMismatchRowIndexes().contains(i)) {
                    row.setNeedsCheck(true);
                    row.setCheckReason("balance_mismatch");
                } else if (pr.confidence < minConfidence) {
                    row.setNeedsCheck(true);
                    row.setCheckReason("low_confidence");
                } else {
                    row.setNeedsCheck(false);
                    row.setCheckReason(null);
                }

                stagingRows.add(row);
            }

            // Match existing transactions
            String tz = statementMatcher.getUserTimezone(imp.getUserId());
            statementMatcher.matchRows(imp.getUserId(), imp.getBankAccountId(), tz, stagingRows);

            // Step 5: Save rows and update import to ready
            rowRepository.saveAll(stagingRows);

            imp.setStatus("ready");
            imp.setExtractionMethod(extracted.getMethod());
            imp.setPageCount(extracted.getPageCount());
            imp.setRowCount(stagingRows.size());
            imp.setFlaggedCount((int) stagingRows.stream().filter(StatementImportRow::isNeedsCheck).count());
            imp.setOpeningBalance(parsedStatement.openingBalance);
            imp.setClosingBalance(parsedStatement.closingBalance);
            imp.setGateStatus(balanceResult.getGateStatus());
            imp.setUpdatedAt(Instant.now());
            importRepository.save(imp);

        } catch (StatementException se) {
            log.warn("StatementException for import id: {}, error: {}", importId, se.getErrorCode());
            importRepository.findById(importId).ifPresent(imp -> failImport(imp, se.getErrorCode()));
        } catch (Exception e) {
            log.error("Unexpected error for import id: {}, exception: {}", importId, e.getClass().getSimpleName());
            importRepository.findById(importId).ifPresent(imp -> failImport(imp, "INTERNAL_ERROR"));
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private String getBankShortCode(UUID bankAccountId) {
        String sql = """
            SELECT b.short_code
            FROM bank_accounts a
            JOIN banks b ON a.bank_id = b.id
            WHERE a.id = ?
            """;
        try {
            return jdbcTemplate.queryForObject(sql, String.class, bankAccountId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void failImport(StatementImport imp, String errorCode) {
        imp.setStatus("failed");
        imp.setErrorCode(errorCode);
        imp.setUpdatedAt(Instant.now());
        importRepository.save(imp);
    }
}
