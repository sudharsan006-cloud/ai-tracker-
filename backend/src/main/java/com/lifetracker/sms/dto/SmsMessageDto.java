package com.lifetracker.sms.dto;

import java.time.Instant;

public class SmsMessageDto {

    private String sender;
    private String rawText;
    private Instant receivedAt;

    public SmsMessageDto() {
    }

    public SmsMessageDto(String sender, String rawText, Instant receivedAt) {
        this.sender = sender;
        this.rawText = rawText;
        this.receivedAt = receivedAt;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
