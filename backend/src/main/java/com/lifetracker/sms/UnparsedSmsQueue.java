package com.lifetracker.sms;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unparsed_sms_queue")
public class UnparsedSmsQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "sender", length = 40)
    private String sender;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "sms_fingerprint", nullable = false, length = 64)
    private String smsFingerprint;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "resolved_transaction_id")
    private UUID resolvedTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UnparsedSmsQueue() {
    }

    public UnparsedSmsQueue(UUID userId, String rawText, String sender, Instant receivedAt, String smsFingerprint) {
        this.userId = userId;
        this.rawText = rawText;
        this.sender = sender;
        this.receivedAt = receivedAt;
        this.smsFingerprint = smsFingerprint;
        this.status = "pending";
        this.createdAt = Instant.now();
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

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getSmsFingerprint() {
        return smsFingerprint;
    }

    public void setSmsFingerprint(String smsFingerprint) {
        this.smsFingerprint = smsFingerprint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getResolvedTransactionId() {
        return resolvedTransactionId;
    }

    public void setResolvedTransactionId(UUID resolvedTransactionId) {
        this.resolvedTransactionId = resolvedTransactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
