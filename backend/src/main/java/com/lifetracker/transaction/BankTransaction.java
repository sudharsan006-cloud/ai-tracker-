package com.lifetracker.transaction;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_transactions")
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    @Column(name = "currency_id", nullable = false)
    private Integer currencyId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "direction", nullable = false, length = 6)
    private String direction;

    @Column(name = "merchant_raw", length = 255)
    private String merchantRaw;

    @Column(name = "merchant_normalized", length = 255)
    private String merchantNormalized;

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    @Column(name = "source", nullable = false, length = 10)
    private String source;

    @Column(name = "sms_fingerprint", length = 64)
    private String smsFingerprint;

    @Column(name = "is_self_transfer", nullable = false)
    private boolean isSelfTransfer = false;

    @Column(name = "self_transfer_matched_id")
    private UUID selfTransferMatchedId;

    @Column(name = "refund_matched_id")
    private UUID refundMatchedId;

    @Column(name = "review_status", nullable = false, length = 30)
    private String reviewStatus = "none";

    @Column(name = "review_partner_id")
    private UUID reviewPartnerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public BankTransaction() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public String getMerchantNormalized() {
        return merchantNormalized;
    }

    public void setMerchantNormalized(String merchantNormalized) {
        this.merchantNormalized = merchantNormalized;
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

    public String getSmsFingerprint() {
        return smsFingerprint;
    }

    public void setSmsFingerprint(String smsFingerprint) {
        this.smsFingerprint = smsFingerprint;
    }

    public boolean isSelfTransfer() {
        return isSelfTransfer;
    }

    public void setSelfTransfer(boolean selfTransfer) {
        isSelfTransfer = selfTransfer;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
