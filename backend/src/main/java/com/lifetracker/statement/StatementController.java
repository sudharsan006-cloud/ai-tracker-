package com.lifetracker.statement;

import com.lifetracker.statement.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/statement-imports")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> uploadStatement(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("bankAccountId") UUID bankAccountId,
            @RequestParam(value = "password", required = false) String password) {
        Map<String, Object> response = statementService.uploadStatement(userId, bankAccountId, file, password);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<StatementImportSummaryResponse>> listImports(
            @AuthenticationPrincipal UUID userId) {
        List<StatementImportSummaryResponse> response = statementService.listImports(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StatementImportSummaryResponse> getImport(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id) {
        StatementImportSummaryResponse response = statementService.getImport(userId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/rows")
    public ResponseEntity<List<StatementImportRowResponse>> getRows(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id) {
        List<StatementImportRowResponse> response = statementService.getRows(userId, id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/rows/{rowId}")
    public ResponseEntity<StatementImportRowResponse> patchRow(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id,
            @PathVariable("rowId") UUID rowId,
            @RequestBody PatchStatementRowRequest request) {
        StatementImportRowResponse response = statementService.patchRow(userId, id, rowId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteImport(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id) {
        statementService.deleteImport(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/commit")
    public ResponseEntity<CommitResponse> commitImport(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id) {
        CommitResponse response = statementService.commitImport(userId, id);
        return ResponseEntity.ok(response);
    }
}
