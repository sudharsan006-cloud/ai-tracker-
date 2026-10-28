package com.lifetracker.statement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ExistingTransactionSummary {
    private UUID id;
    private BigDecimal amount;
    private String direction;
    private Instant transactionDate;
    private String merchantRaw;

    public ExistingTransactionSummary() {
    }

    public ExistingTransactionSummary(UUID id, BigDecimal amount, String direction, Instant transactionDate, String merchantRaw) {
        this.id = id;
        this.amount = amount;
        this.direction = direction;
        this.transactionDate = transactionDate;
        this.merchantRaw = merchantRaw;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getMerchantRaw() {
        return merchantRaw;
    }

    public void setMerchantRaw(String merchantRaw) {
        this.merchantRaw = merchantRaw;
    }
}
