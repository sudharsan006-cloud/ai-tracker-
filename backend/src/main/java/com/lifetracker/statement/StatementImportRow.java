package com.lifetracker.statement;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "statement_import_rows")
public class StatementImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "txn_date")
    private LocalDate txnDate;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "direction", length = 6)
    private String direction;

    @Column(name = "balance_after", precision = 14, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "confidence", precision = 5, scale = 2)
    private Double confidence;

    @Column(name = "needs_check", nullable = false)
    private boolean needsCheck = false;

    @Column(name = "check_reason", length = 30)
    private String checkReason;

    @Column(name = "selected", nullable = false)
    private boolean selected = true;

    @Column(name = "existing_transaction_id")
    private UUID existingTransactionId;

    public StatementImportRow() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getImportId() {
        return importId;
    }

    public void setImportId(UUID importId) {
        this.importId = importId;
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

    public UUID getExistingTransactionId() {
        return existingTransactionId;
    }

    public void setExistingTransactionId(UUID existingTransactionId) {
        this.existingTransactionId = existingTransactionId;
    }
}
