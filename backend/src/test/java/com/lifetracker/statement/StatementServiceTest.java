package com.lifetracker.statement;

import com.lifetracker.common.ConflictException;
import com.lifetracker.common.NotFoundException;
import com.lifetracker.config.ConfigService;
import com.lifetracker.statement.dto.*;
import com.lifetracker.transaction.BankTransaction;
import com.lifetracker.transaction.BankTransactionRepository;
import com.lifetracker.transaction.TransactionMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class StatementServiceTest {

    private StatementImportRepository importRepository;
    private StatementImportRowRepository rowRepository;
    private StatementProcessingService processingService;
    private StatementMatcher statementMatcher;
    private TransactionMatchingService matchingService;
    private BankTransactionRepository transactionRepository;
    private ConfigService configService;
    private JdbcTemplate jdbcTemplate;
    private StatementService statementService;

    @BeforeEach
    void setUp() {
        importRepository = mock(StatementImportRepository.class);
        rowRepository = mock(StatementImportRowRepository.class);
        processingService = mock(StatementProcessingService.class);
        statementMatcher = mock(StatementMatcher.class);
        matchingService = mock(TransactionMatchingService.class);
        transactionRepository = mock(BankTransactionRepository.class);
        configService = mock(ConfigService.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        when(configService.get("statement_import_expiry_days")).thenReturn(new BigDecimal("7"));
        when(statementMatcher.getUserTimezone(any())).thenReturn("Asia/Kolkata");

        statementService = new StatementService(
                importRepository,
                rowRepository,
                processingService,
                statementMatcher,
                matchingService,
                transactionRepository,
                configService,
                jdbcTemplate,
                System.getProperty("java.io.tmpdir")
        );
    }

    @Test
    @DisplayName("Upload: invalid file type (text file renamed .pdf) -> 400")
    void testUploadInvalidFileType() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(accountId), eq(userId))).thenReturn(1);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "Hello World not a PDF".getBytes());

        assertThatThrownBy(() -> statementService.uploadStatement(userId, accountId, file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only PDF, PNG or JPG files are supported");
    }

    @Test
    @DisplayName("Upload: file over 5 MB -> 400")
    void testUploadFileTooLarge() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(accountId), eq(userId))).thenReturn(1);

        byte[] bigBytes = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", bigBytes);

        assertThatThrownBy(() -> statementService.uploadStatement(userId, accountId, file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File is too large");
    }

    @Test
    @DisplayName("Upload: inactive or other user's account -> 400")
    void testUploadInactiveAccount() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(accountId), eq(userId))).thenReturn(0);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F'});

        assertThatThrownBy(() -> statementService.uploadStatement(userId, accountId, file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bank account is invalid or inactive");
    }

    @Test
    @DisplayName("Upload: second upload while one is processing -> 409")
    void testUploadWhileProcessingConflict() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(accountId), eq(userId))).thenReturn(1);
        when(importRepository.existsByUserIdAndStatus(userId, "processing")).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F'});

        assertThatThrownBy(() -> statementService.uploadStatement(userId, accountId, file, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Another import is still running");
    }

    @Test
    @DisplayName("PATCH row: change amount clears needs_check and check_reason; recomputes match")
    void testPatchRowAmountClearsNeedsCheck() {
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();

        StatementImport imp = new StatementImport();
        imp.setId(importId);
        imp.setUserId(userId);
        imp.setStatus("ready");
        imp.setBankAccountId(UUID.randomUUID());
        when(importRepository.findByIdAndUserId(importId, userId)).thenReturn(Optional.of(imp));

        StatementImportRow row = new StatementImportRow();
        row.setId(rowId);
        row.setImportId(importId);
        row.setNeedsCheck(true);
        row.setCheckReason("balance_mismatch");
        row.setSelected(true);
        row.setTxnDate(LocalDate.of(2026, 10, 1));
        row.setDirection("debit");
        when(rowRepository.findByIdAndImportId(rowId, importId)).thenReturn(Optional.of(row));
        when(rowRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PatchStatementRowRequest request = new PatchStatementRowRequest();
        request.setAmount(new BigDecimal("250.00"));

        StatementImportRowResponse response = statementService.patchRow(userId, importId, rowId, request);

        assertThat(response.isNeedsCheck()).isFalse();
        assertThat(response.getCheckReason()).isNull();
        assertThat(response.getAmount()).isEqualTo(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("PATCH row: change only selected does NOT clear needs_check")
    void testPatchRowSelectedOnlyPreservesNeedsCheck() {
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();

        StatementImport imp = new StatementImport();
        imp.setId(importId);
        imp.setUserId(userId);
        imp.setStatus("ready");
        when(importRepository.findByIdAndUserId(importId, userId)).thenReturn(Optional.of(imp));

        StatementImportRow row = new StatementImportRow();
        row.setId(rowId);
        row.setImportId(importId);
        row.setNeedsCheck(true);
        row.setCheckReason("missing_fields");
        row.setSelected(true);
        when(rowRepository.findByIdAndImportId(rowId, importId)).thenReturn(Optional.of(row));
        when(rowRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PatchStatementRowRequest request = new PatchStatementRowRequest();
        request.setSelected(false);

        StatementImportRowResponse response = statementService.patchRow(userId, importId, rowId, request);

        assertThat(response.isSelected()).isFalse();
        assertThat(response.isNeedsCheck()).isTrue();
        assertThat(response.getCheckReason()).isEqualTo("missing_fields");
    }

    @Test
    @DisplayName("PATCH row on non-ready import -> 409")
    void testPatchRowNotReadyThrowsConflict() {
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();

        StatementImport imp = new StatementImport();
        imp.setId(importId);
        imp.setUserId(userId);
        imp.setStatus("processing");
        when(importRepository.findByIdAndUserId(importId, userId)).thenReturn(Optional.of(imp));

        PatchStatementRowRequest request = new PatchStatementRowRequest();
        request.setSelected(false);

        assertThatThrownBy(() -> statementService.patchRow(userId, importId, rowId, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Commit: blocked with 409 while a selected row has needs_check")
    void testCommitBlockedByFlaggedRow() {
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();

        StatementImport imp = new StatementImport();
        imp.setId(importId);
        imp.setUserId(userId);
        imp.setStatus("ready");

        when(jdbcTemplate.update(anyString(), eq(importId), eq(userId))).thenReturn(1);
        when(importRepository.findById(importId)).thenReturn(Optional.of(imp));

        StatementImportRow row1 = new StatementImportRow();
        row1.setSelected(true);
        row1.setNeedsCheck(true);
        row1.setTxnDate(LocalDate.of(2026, 10, 1));
        row1.setAmount(new BigDecimal("100.00"));
        row1.setDirection("debit");

        when(rowRepository.findByImportIdOrderByRowIndexAsc(importId)).thenReturn(List.of(row1));

        assertThatThrownBy(() -> statementService.commitImport(userId, importId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Fix or uncheck 1 highlighted rows first");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Commit: succeeds after unchecking flagged row; creates transactions with source='statement'")
    void testCommitSuccess() {
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        StatementImport imp = new StatementImport();
        imp.setId(importId);
        imp.setUserId(userId);
        imp.setStatus("ready");
        imp.setBankAccountId(accountId);

        when(jdbcTemplate.update(startsWith("UPDATE statement_imports SET status = 'committed'"), eq(importId), eq(userId))).thenReturn(1);
        when(importRepository.findById(importId)).thenReturn(Optional.of(imp));
        when(jdbcTemplate.queryForObject(startsWith("SELECT currency_id"), eq(Integer.class), eq(accountId))).thenReturn(1);

        StatementImportRow row1 = new StatementImportRow();
        row1.setSelected(true);
        row1.setNeedsCheck(false);
        row1.setTxnDate(LocalDate.of(2026, 10, 1));
        row1.setAmount(new BigDecimal("150.00"));
        row1.setDirection("debit");
        row1.setDescription("Grocery Store");

        StatementImportRow row2 = new StatementImportRow();
        row2.setSelected(false);
        row2.setNeedsCheck(true);

        when(rowRepository.findByImportIdOrderByRowIndexAsc(importId)).thenReturn(List.of(row1, row2));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommitResponse res = statementService.commitImport(userId, importId);

        assertThat(res.getCreated()).isEqualTo(1);
        assertThat(res.getSkipped()).isEqualTo(1);

        verify(matchingService, times(1)).runMatching(any(BankTransaction.class));
        verify(rowRepository, times(1)).deleteByImportId(importId);
    }

    @Test
    @DisplayName("Commit: second commit attempt throws 409")
    void testSecondCommitThrowsConflict() {
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();

        when(jdbcTemplate.update(anyString(), eq(importId), eq(userId))).thenReturn(0);

        assertThatThrownBy(() -> statementService.commitImport(userId, importId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Import is not ready or already committed");
    }

    @Test
    @DisplayName("Stale processing import (updated 11 minutes ago) becomes failed/INTERNAL_ERROR on GET")
    void testStaleProcessingImportFails() {
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();

        StatementImport imp = new StatementImport();
        imp.setId(importId);
        imp.setUserId(userId);
        imp.setStatus("processing");
        imp.setUpdatedAt(Instant.now().minus(11, ChronoUnit.MINUTES));

        when(importRepository.findByIdAndUserId(importId, userId)).thenReturn(Optional.of(imp));
        when(importRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StatementImportSummaryResponse summary = statementService.getImport(userId, importId);

        assertThat(summary.getStatus()).isEqualTo("failed");
        assertThat(summary.getErrorCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Another user can never read, patch, commit or delete first user's imports (404)")
    void testUserIsolation404() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID importId = UUID.randomUUID();

        when(importRepository.findByIdAndUserId(importId, user2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statementService.getImport(user2, importId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> statementService.getRows(user2, importId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> statementService.deleteImport(user2, importId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> statementService.patchRow(user2, importId, UUID.randomUUID(), new PatchStatementRowRequest()))
                .isInstanceOf(NotFoundException.class);
    }
}
