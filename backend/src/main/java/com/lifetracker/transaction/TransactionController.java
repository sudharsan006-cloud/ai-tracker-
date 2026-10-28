package com.lifetracker.transaction;

import com.lifetracker.transaction.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse response = transactionService.createTransaction(userId, request, "manual", null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<TransactionPageResponse> getTransactions(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "bankAccountId", required = false) UUID bankAccountId,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        TransactionPageResponse response = transactionService.getTransactions(
                userId, from, to, categoryId, bankAccountId, direction, page, size);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TransactionResponse> patchTransaction(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id,
            @Valid @RequestBody PatchTransactionRequest request) {
        TransactionResponse response = transactionService.patchTransaction(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id) {
        transactionService.deleteTransaction(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unlink")
    public ResponseEntity<Void> unlinkTransaction(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id) {
        transactionService.unlinkTransaction(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/review-queue")
    public ResponseEntity<ReviewQueueResponse> getReviewQueue(@AuthenticationPrincipal UUID userId) {
        ReviewQueueResponse response = transactionService.getReviewQueue(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/review/confirm")
    public ResponseEntity<Void> confirmReview(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id) {
        transactionService.confirmReview(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/review/dismiss")
    public ResponseEntity<Void> dismissReview(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id) {
        transactionService.dismissReview(userId, id);
        return ResponseEntity.noContent().build();
    }
}
