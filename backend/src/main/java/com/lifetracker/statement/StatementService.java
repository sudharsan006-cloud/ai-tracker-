package com.lifetracker.statement;

import com.lifetracker.common.ConflictException;
import com.lifetracker.common.NotFoundException;
import com.lifetracker.config.ConfigService;
import com.lifetracker.statement.dto.*;
import com.lifetracker.transaction.BankTransaction;
import com.lifetracker.transaction.BankTransactionRepository;
import com.lifetracker.transaction.MerchantNormalizer;
import com.lifetracker.transaction.TransactionMatchingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class StatementService {

    private final StatementImportRepository importRepository;
    private final StatementImportRowRepository rowRepository;
    private final StatementProcessingService processingService;
    private final StatementMatcher statementMatcher;
    private final TransactionMatchingService matchingService;
    private final BankTransactionRepository transactionRepository;
    private final ConfigService configService;
    private final JdbcTemplate jdbcTemplate;
    private final String tmpDir;

    public StatementService(
            StatementImportRepository importRepository,
            StatementImportRowRepository rowRepository,
            StatementProcessingService processingService,
            StatementMatcher statementMatcher,
            TransactionMatchingService matchingService,
            BankTransactionRepository transactionRepository,
            ConfigService configService,
            JdbcTemplate jdbcTemplate,
            @Value("${app.statement.tmp-dir:${java.io.tmpdir}}") String tmpDir) {
        this.importRepository = importRepository;
        this.rowRepository = rowRepository;
        this.processingService = processingService;
        this.statementMatcher = statementMatcher;
        this.matchingService = matchingService;
        this.transactionRepository = transactionRepository;
        this.configService = configService;
        this.jdbcTemplate = jdbcTemplate;
        this.tmpDir = tmpDir;
    }

    public Map<String, Object> uploadStatement(UUID userId, UUID bankAccountId, MultipartFile file, String password) {
        // Validation 1: bankAccountId must be an ACTIVE account of the logged-in user
        String accountCheckSql = "SELECT count(*) FROM bank_accounts WHERE id = ? AND user_id = ? AND is_active = true";
        Integer activeCount = jdbcTemplate.queryForObject(accountCheckSql, Integer.class, bankAccountId, userId);
        if (activeCount == null || activeCount == 0) {
            throw new IllegalArgumentException("Bank account is invalid or inactive");
        }

        // Validation 2: File must be non-empty and at most 5 MB
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File is too large (max 5 MB)");
        }

        // Validation 3: Detect real file type from first bytes
        String detectedType = detectFileType(file);
        if (detectedType == null) {
            throw new IllegalArgumentException("Only PDF, PNG or JPG files are supported");
        }

        // Validation 4: If user already has an import with status 'processing' -> 409 CONFLICT
        if (importRepository.existsByUserIdAndStatus(userId, "processing")) {
            throw new ConflictException("Another import is still running");
        }

        // Validation 5: Lazy cleanup of expired uncommitted imports
        BigDecimal expiryDaysVal = configService.get("statement_import_expiry_days");
        int expiryDays = expiryDaysVal != null ? expiryDaysVal.intValue() : 7;
        Instant cutoff = Instant.now().minus(Duration.ofDays(expiryDays));
        importRepository.deleteExpiredImports(userId, cutoff);

        // Create statement_imports row
        StatementImport imp = new StatementImport();
        imp.setUserId(userId);
        imp.setBankAccountId(bankAccountId);
        imp.setFileType(detectedType);
        imp.setStatus("processing");
        imp.setCreatedAt(Instant.now());
        imp.setUpdatedAt(Instant.now());
        imp = importRepository.save(imp);

        // Save file to temp file (UUID + detectedType)
        File tempDir = new File(tmpDir);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File tempFile = new File(tempDir, UUID.randomUUID() + "." + detectedType);
        try {
            file.transferTo(tempFile);
        } catch (IOException e) {
            imp.setStatus("failed");
            imp.setErrorCode("INTERNAL_ERROR");
            importRepository.save(imp);
            throw new RuntimeException("Could not save uploaded file", e);
        }

        // Start background processing
        processingService.process(imp.getId(), tempFile.getAbsolutePath(), password);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", imp.getId().toString());
        result.put("status", "processing");
        return result;
    }

    public List<StatementImportSummaryResponse> listImports(UUID userId) {
        List<StatementImport> list = importRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 20));
        List<StatementImportSummaryResponse> summaries = new ArrayList<>();
        for (StatementImport imp : list) {
            checkAndExpireStale(imp);
            long selected = rowRepository.countSelectedByImportId(imp.getId());
            long blocking = rowRepository.countBlockingByImportId(imp.getId());
            summaries.add(StatementImportSummaryResponse.fromEntity(imp, selected, blocking));
        }
        return summaries;
    }

    public StatementImportSummaryResponse getImport(UUID userId, UUID importId) {
        StatementImport imp = importRepository.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new NotFoundException("Statement import not found"));

        checkAndExpireStale(imp);

        long selected = rowRepository.countSelectedByImportId(imp.getId());
        long blocking = rowRepository.countBlockingByImportId(imp.getId());
        return StatementImportSummaryResponse.fromEntity(imp, selected, blocking);
    }

    public List<StatementImportRowResponse> getRows(UUID userId, UUID importId) {
        importRepository.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new NotFoundException("Statement import not found"));

        List<StatementImportRow> rows = rowRepository.findByImportIdOrderByRowIndexAsc(importId);
        List<StatementImportRowResponse> responseList = new ArrayList<>();

        for (StatementImportRow row : rows) {
            ExistingTransactionSummary existingTx = null;
            if (row.getExistingTransactionId() != null) {
                existingTx = findExistingTransaction(row.getExistingTransactionId());
            }
            responseList.add(StatementImportRowResponse.fromEntity(row, existingTx));
        }

        return responseList;
    }

    public StatementImportRowResponse patchRow(UUID userId, UUID importId, UUID rowId, PatchStatementRowRequest request) {
        StatementImport imp = importRepository.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new NotFoundException("Statement import not found"));

        if (!"ready".equalsIgnoreCase(imp.getStatus())) {
            throw new ConflictException("Import is not in ready status");
        }

        StatementImportRow row = rowRepository.findByIdAndImportId(rowId, importId)
                .orElseThrow(() -> new NotFoundException("Statement row not found"));

        boolean coreFieldChanged = false;

        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0 || request.getAmount().scale() > 2) {
                throw new IllegalArgumentException("Amount must be greater than zero with at most 2 decimal places");
            }
            row.setAmount(request.getAmount());
            coreFieldChanged = true;
        }

        if (request.getDirection() != null) {
            String dir = request.getDirection().toLowerCase();
            if (!"debit".equals(dir) && !"credit".equals(dir)) {
                throw new IllegalArgumentException("Direction must be debit or credit");
            }
            row.setDirection(dir);
            coreFieldChanged = true;
        }

        if (request.getDescription() != null) {
            if (request.getDescription().length() > 255) {
                throw new IllegalArgumentException("Description cannot exceed 255 characters");
            }
            row.setDescription(request.getDescription());
            coreFieldChanged = true;
        }

        if (request.getTxnDate() != null) {
            LocalDate date = LocalDate.parse(request.getTxnDate());
            row.setTxnDate(date);
            coreFieldChanged = true;
        }

        if (coreFieldChanged) {
            row.setNeedsCheck(false);
            row.setCheckReason(null);
            String tz = statementMatcher.getUserTimezone(userId);
            UUID matched = statementMatcher.findSingleMatch(
                    userId,
                    imp.getBankAccountId(),
                    tz,
                    row.getTxnDate(),
                    row.getAmount(),
                    row.getDirection(),
                    null
            );
            row.setExistingTransactionId(matched);
        }

        if (request.getSelected() != null) {
            row.setSelected(request.getSelected());
        }

        row = rowRepository.save(row);

        ExistingTransactionSummary existingTx = null;
        if (row.getExistingTransactionId() != null) {
            existingTx = findExistingTransaction(row.getExistingTransactionId());
        }

        return StatementImportRowResponse.fromEntity(row, existingTx);
    }

    public void deleteImport(UUID userId, UUID importId) {
        StatementImport imp = importRepository.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new NotFoundException("Statement import not found"));

        importRepository.delete(imp);
    }

    @Transactional
    public CommitResponse commitImport(UUID userId, UUID importId) {
        // Step 1: Atomic update status from ready to committed
        String updateSql = "UPDATE statement_imports SET status = 'committed', updated_at = now() WHERE id = ? AND user_id = ? AND status = 'ready'";
        int rowsUpdated = jdbcTemplate.update(updateSql, importId, userId);
        if (rowsUpdated == 0) {
            throw new ConflictException("Import is not ready or already committed");
        }

        StatementImport imp = importRepository.findById(importId).orElseThrow();

        // Step 2: Check for blocking rows among selected
        List<StatementImportRow> allRows = rowRepository.findByImportIdOrderByRowIndexAsc(importId);
        List<StatementImportRow> selectedRows = allRows.stream().filter(StatementImportRow::isSelected).toList();

        long blockingCount = selectedRows.stream()
                .filter(r -> r.isNeedsCheck() || r.getTxnDate() == null || r.getAmount() == null || r.getDirection() == null)
                .count();

        if (blockingCount > 0) {
            throw new ConflictException("Fix or uncheck " + blockingCount + " highlighted rows first");
        }

        // Fetch currency from bank account
        String currencySql = "SELECT currency_id FROM bank_accounts WHERE id = ?";
        Integer currencyId = jdbcTemplate.queryForObject(currencySql, Integer.class, imp.getBankAccountId());
        if (currencyId == null) {
            currencyId = 1;
        }

        String tz = statementMatcher.getUserTimezone(userId);
        ZoneId zoneId = ZoneId.of(tz);

        // Step 3 & 4: Create transactions and run matching
        for (StatementImportRow row : selectedRows) {
            BankTransaction tx = new BankTransaction();
            tx.setUserId(userId);
            tx.setBankAccountId(imp.getBankAccountId());
            tx.setCurrencyId(currencyId);
            tx.setCategoryId(null);
            tx.setAmount(row.getAmount());
            tx.setDirection(row.getDirection().toLowerCase());

            String desc = row.getDescription() != null ? row.getDescription() : "";
            if (desc.length() > 255) {
                desc = desc.substring(0, 255);
            }
            tx.setMerchantRaw(desc);
            tx.setMerchantNormalized(MerchantNormalizer.normalizeMerchant(desc));

            Instant txDate = row.getTxnDate().atTime(12, 0).atZone(zoneId).toInstant();
            tx.setTransactionDate(txDate);
            tx.setSource("statement");
            tx.setSelfTransfer(false);
            tx.setReviewStatus("none");

            tx = transactionRepository.save(tx);
            matchingService.runMatching(tx);
        }

        int created = selectedRows.size();
        int skipped = allRows.size() - created;

        // Step 5: Delete staging rows and update committed_count
        rowRepository.deleteByImportId(importId);
        jdbcTemplate.update("UPDATE statement_imports SET committed_count = ? WHERE id = ?", created, importId);

        return new CommitResponse(created, skipped);
    }

    private void checkAndExpireStale(StatementImport imp) {
        if ("processing".equalsIgnoreCase(imp.getStatus())) {
            Instant tenMinsAgo = Instant.now().minus(Duration.ofMinutes(10));
            if (imp.getUpdatedAt().isBefore(tenMinsAgo)) {
                imp.setStatus("failed");
                imp.setErrorCode("INTERNAL_ERROR");
                imp.setUpdatedAt(Instant.now());
                importRepository.save(imp);
            }
        }
    }

    private ExistingTransactionSummary findExistingTransaction(UUID txId) {
        String sql = "SELECT id, amount, direction, transaction_date, merchant_raw FROM bank_transactions WHERE id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                ExistingTransactionSummary s = new ExistingTransactionSummary();
                s.setId((UUID) rs.getObject("id"));
                s.setAmount(rs.getBigDecimal("amount"));
                s.setDirection(rs.getString("direction"));
                s.setTransactionDate(rs.getTimestamp("transaction_date").toInstant());
                s.setMerchantRaw(rs.getString("merchant_raw"));
                return s;
            }, txId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private String detectFileType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[4];
            int read = is.read(header);
            if (read < 3) {
                return null;
            }

            // PDF: %PDF -> 0x25, 0x50, 0x44, 0x46
            if (read >= 4 && header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46) {
                return "pdf";
            }
            // PNG: 89 50 4E 47 -> 0x89, 0x50, 0x4E, 0x47
            if (read >= 4 && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                return "png";
            }
            // JPG: FF D8 FF -> 0xFF, 0xD8, 0xFF
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                return "jpg";
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
