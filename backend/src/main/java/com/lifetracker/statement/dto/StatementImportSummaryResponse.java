package com.lifetracker.statement.dto;

import com.lifetracker.statement.StatementImport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class StatementImportSummaryResponse {
    private UUID id;
    private UUID bankAccountId;
    private String fileType;
    private String extractionMethod;
    private String status;
    private String errorCode;
    private Integer pageCount;
    private Integer rowCount;
    private Integer flaggedCount;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private String gateStatus;
    private long selectedCount;
    private long blockingCount;
    private Integer committedCount;
    private Instant createdAt;

    public StatementImportSummaryResponse() {
    }

    public static StatementImportSummaryResponse fromEntity(StatementImport imp, long selectedCount, long blockingCount) {
        StatementImportSummaryResponse res = new StatementImportSummaryResponse();
        res.setId(imp.getId());
        res.setBankAccountId(imp.getBankAccountId());
        res.setFileType(imp.getFileType());
        res.setExtractionMethod(imp.getExtractionMethod());
        res.setStatus(imp.getStatus());
        res.setErrorCode(imp.getErrorCode());
        res.setPageCount(imp.getPageCount());
        res.setRowCount(imp.getRowCount());
        res.setFlaggedCount(imp.getFlaggedCount());
        res.setOpeningBalance(imp.getOpeningBalance());
        res.setClosingBalance(imp.getClosingBalance());
        res.setGateStatus(imp.getGateStatus());
        res.setSelectedCount(selectedCount);
        res.setBlockingCount(blockingCount);
        res.setCommittedCount(imp.getCommittedCount());
        res.setCreatedAt(imp.getCreatedAt());
        return res;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(UUID bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getExtractionMethod() {
        return extractionMethod;
    }

    public void setExtractionMethod(String extractionMethod) {
        this.extractionMethod = extractionMethod;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public Integer getFlaggedCount() {
        return flaggedCount;
    }

    public void setFlaggedCount(Integer flaggedCount) {
        this.flaggedCount = flaggedCount;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(BigDecimal closingBalance) {
        this.closingBalance = closingBalance;
    }

    public String getGateStatus() {
        return gateStatus;
    }

    public void setGateStatus(String gateStatus) {
        this.gateStatus = gateStatus;
    }

    public long getSelectedCount() {
        return selectedCount;
    }

    public void setSelectedCount(long selectedCount) {
        this.selectedCount = selectedCount;
    }

    public long getBlockingCount() {
        return blockingCount;
    }

    public void setBlockingCount(long blockingCount) {
        this.blockingCount = blockingCount;
    }

    public Integer getCommittedCount() {
        return committedCount;
    }

    public void setCommittedCount(Integer committedCount) {
        this.committedCount = committedCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
