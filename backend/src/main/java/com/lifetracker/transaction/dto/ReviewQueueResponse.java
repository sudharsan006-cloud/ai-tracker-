package com.lifetracker.transaction.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ReviewQueueResponse {

    private List<TransactionPair> duplicates;
    private List<TransactionPair> selfTransfers;
    private List<TransactionPair> refundMatches;
    private List<UnparsedSmsItem> unparsedSms;

    public ReviewQueueResponse() {
    }

    public ReviewQueueResponse(
            List<TransactionPair> duplicates,
            List<TransactionPair> selfTransfers,
            List<TransactionPair> refundMatches,
            List<UnparsedSmsItem> unparsedSms) {
        this.duplicates = duplicates;
        this.selfTransfers = selfTransfers;
        this.refundMatches = refundMatches;
        this.unparsedSms = unparsedSms;
    }

    public static class TransactionPair {
        private TransactionResponse transaction;
        private TransactionResponse partner;

        public TransactionPair() {
        }

        public TransactionPair(TransactionResponse transaction, TransactionResponse partner) {
            this.transaction = transaction;
            this.partner = partner;
        }

        public TransactionResponse getTransaction() {
            return transaction;
        }

        public void setTransaction(TransactionResponse transaction) {
            this.transaction = transaction;
        }

        public TransactionResponse getPartner() {
            return partner;
        }

        public void setPartner(TransactionResponse partner) {
            this.partner = partner;
        }
    }

    public static class UnparsedSmsItem {
        private UUID id;
        private String rawText;
        private String sender;
        private Instant receivedAt;

        public UnparsedSmsItem() {
        }

        public UnparsedSmsItem(UUID id, String rawText, String sender, Instant receivedAt) {
            this.id = id;
            this.rawText = rawText;
            this.sender = sender;
            this.receivedAt = receivedAt;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
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
    }

    public List<TransactionPair> getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(List<TransactionPair> duplicates) {
        this.duplicates = duplicates;
    }

    public List<TransactionPair> getSelfTransfers() {
        return selfTransfers;
    }

    public void setSelfTransfers(List<TransactionPair> selfTransfers) {
        this.selfTransfers = selfTransfers;
    }

    public List<TransactionPair> getRefundMatches() {
        return refundMatches;
    }

    public void setRefundMatches(List<TransactionPair> refundMatches) {
        this.refundMatches = refundMatches;
    }

    public List<UnparsedSmsItem> getUnparsedSms() {
        return unparsedSms;
    }

    public void setUnparsedSms(List<UnparsedSmsItem> unparsedSms) {
        this.unparsedSms = unparsedSms;
    }
}
