package com.lifetracker.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PatchTransactionRequest {

    private UUID categoryId;
    private String merchant;

    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @Digits(integer = 12, fraction = 2, message = "amount can have at most 2 decimal places")
    private BigDecimal amount;

    @Pattern(regexp = "^(debit|credit)$", message = "direction must be 'debit' or 'credit'")
    private String direction;

    private Instant transactionDate;
    private UUID bankAccountId;

    public PatchTransactionRequest() {
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Instant getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Instant transactionDate) {
        this.transactionDate = transactionDate;
    }

    public UUID getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(UUID bankAccountId) {
        this.bankAccountId = bankAccountId;
    }
}
