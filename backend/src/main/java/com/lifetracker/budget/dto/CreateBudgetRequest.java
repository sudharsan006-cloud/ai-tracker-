package com.lifetracker.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class CreateBudgetRequest {

    private UUID categoryId;

    @NotNull(message = "currencyId is required")
    private Integer currencyId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @Digits(integer = 12, fraction = 2, message = "amount can have at most 2 decimal places")
    private BigDecimal amount;

    public CreateBudgetRequest() {
    }

    public CreateBudgetRequest(UUID categoryId, Integer currencyId, BigDecimal amount) {
        this.categoryId = categoryId;
        this.currencyId = currencyId;
        this.amount = amount;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Integer currencyId) {
        this.currencyId = currencyId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
