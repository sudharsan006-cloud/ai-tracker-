package com.lifetracker.budget;

import com.lifetracker.budget.dto.BudgetResponse;
import com.lifetracker.budget.dto.CreateBudgetRequest;
import com.lifetracker.budget.dto.PatchBudgetRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public ResponseEntity<List<BudgetResponse>> getBudgets(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(budgetService.getBudgets(userId));
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> createBudget(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateBudgetRequest request) {
        BudgetResponse response = budgetService.createBudget(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BudgetResponse> patchBudget(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id,
            @Valid @RequestBody PatchBudgetRequest request) {
        BudgetResponse response = budgetService.patchBudget(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id) {
        budgetService.deleteBudget(userId, id);
        return ResponseEntity.noContent().build();
    }
}
