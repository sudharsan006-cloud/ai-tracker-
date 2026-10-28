package com.lifetracker.statement.dto;

import com.lifetracker.statement.StatementImportRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class StatementImportRowResponse {
    private UUID id;
    private int rowIndex;
    private LocalDate txnDate;
    private String description;
    private BigDecimal amount;
    private String direction;
    private BigDecimal balanceAfter;
    private Double confidence;
    private boolean needsCheck;
    private String checkReason;
    private boolean selected;
    private ExistingTransactionSummary existingTransaction;

    public StatementImportRowResponse() {
    }

    public static StatementImportRowResponse fromEntity(StatementImportRow row, ExistingTransactionSummary existingTx) {
        StatementImportRowResponse res = new StatementImportRowResponse();
        res.setId(row.getId());
        res.setRowIndex(row.getRowIndex());
        res.setTxnDate(row.getTxnDate());
        res.setDescription(row.getDescription());
        res.setAmount(row.getAmount());
        res.setDirection(row.getDirection());
        res.setBalanceAfter(row.getBalanceAfter());
        res.setConfidence(row.getConfidence());
        res.setNeedsCheck(row.isNeedsCheck());
        res.setCheckReason(row.getCheckReason());
        res.setSelected(row.isSelected());
        res.setExistingTransaction(existingTx);
        return res;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public boolean isNeedsCheck() {
        return needsCheck;
    }

    public void setNeedsCheck(boolean needsCheck) {
        this.needsCheck = needsCheck;
    }

    public String getCheckReason() {
        return checkReason;
    }

    public void setCheckReason(String checkReason) {
        this.checkReason = checkReason;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public ExistingTransactionSummary getExistingTransaction() {
        return existingTransaction;
    }

    public void setExistingTransaction(ExistingTransactionSummary existingTransaction) {
        this.existingTransaction = existingTransaction;
    }
}
