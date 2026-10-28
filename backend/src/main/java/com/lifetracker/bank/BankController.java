package com.lifetracker.bank;

import com.lifetracker.bank.dto.BankAccountResponse;
import com.lifetracker.bank.dto.CreateBankAccountRequest;
import com.lifetracker.bank.dto.PatchBankAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class BankController {

    private final BankService bankService;

    public BankController(BankService bankService) {
        this.bankService = bankService;
    }

    @GetMapping("/banks")
    public ResponseEntity<List<Bank>> getBanks() {
        return ResponseEntity.ok(bankService.getActiveBanks());
    }

    @GetMapping("/currencies")
    public ResponseEntity<List<Currency>> getCurrencies() {
        return ResponseEntity.ok(bankService.getCurrencies());
    }

    @GetMapping("/bank-accounts")
    public ResponseEntity<List<BankAccountResponse>> getBankAccounts(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(bankService.getUserBankAccounts(userId));
    }

    @PostMapping("/bank-accounts")
    public ResponseEntity<BankAccountResponse> createBankAccount(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateBankAccountRequest request) {
        BankAccountResponse created = bankService.createBankAccount(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/bank-accounts/{id}")
    public ResponseEntity<BankAccountResponse> patchBankAccount(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID accountId,
            @Valid @RequestBody PatchBankAccountRequest request) {
        BankAccountResponse updated = bankService.patchBankAccount(userId, accountId, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/bank-accounts/{id}")
    public ResponseEntity<Void> deleteBankAccount(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID accountId) {
        bankService.deleteBankAccount(userId, accountId);
        return ResponseEntity.noContent().build();
    }
}
