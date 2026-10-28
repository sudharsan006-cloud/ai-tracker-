package com.lifetracker.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifetracker.transaction.BankTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TransactionResponse {
    private UUID id;
    private UUID bankAccountId;
    private Integer currencyId;
    private UUID categoryId;
    private BigDecimal amount;
    private String direction;
    private String merchantRaw;
    private Instant transactionDate;
    private String source;

    @JsonProperty("isSelfTransfer")
    private boolean isSelfTransfer;

    private String reviewStatus;
    private UUID reviewPartnerId;
    private UUID selfTransferMatchedId;
    private UUID refundMatchedId;

    public TransactionResponse() {
    }

    public static TransactionResponse fromEntity(BankTransaction entity) {
        TransactionResponse dto = new TransactionResponse();
        dto.setId(entity.getId());
        dto.setBankAccountId(entity.getBankAccountId());
        dto.setCurrencyId(entity.getCurrencyId());
        dto.setCategoryId(entity.getCategoryId());
        dto.setAmount(entity.getAmount());
        dto.setDirection(entity.getDirection());
        dto.setMerchantRaw(entity.getMerchantRaw());
        dto.setTransactionDate(entity.getTransactionDate());
        dto.setSource(entity.getSource());
        dto.setSelfTransfer(entity.isSelfTransfer());
        dto.setReviewStatus(entity.getReviewStatus());
        dto.setReviewPartnerId(entity.getReviewPartnerId());
        dto.setSelfTransferMatchedId(entity.getSelfTransferMatchedId());
        dto.setRefundMatchedId(entity.getRefundMatchedId());
        return dto;
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

    public Integer getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Integer currencyId) {
        this.currencyId = currencyId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
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

    public String getMerchantRaw() {
        return merchantRaw;
    }

    public void setMerchantRaw(String merchantRaw) {
        this.merchantRaw = merchantRaw;
    }

    public Instant getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Instant transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isSelfTransfer() {
        return isSelfTransfer;
    }

    public void setSelfTransfer(boolean selfTransfer) {
        isSelfTransfer = selfTransfer;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public UUID getReviewPartnerId() {
        return reviewPartnerId;
    }

    public void setReviewPartnerId(UUID reviewPartnerId) {
        this.reviewPartnerId = reviewPartnerId;
    }

    public UUID getSelfTransferMatchedId() {
        return selfTransferMatchedId;
    }

    public void setSelfTransferMatchedId(UUID selfTransferMatchedId) {
        this.selfTransferMatchedId = selfTransferMatchedId;
    }

    public UUID getRefundMatchedId() {
        return refundMatchedId;
    }

    public void setRefundMatchedId(UUID refundMatchedId) {
        this.refundMatchedId = refundMatchedId;
    }
}
