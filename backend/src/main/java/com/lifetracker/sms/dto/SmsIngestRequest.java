package com.lifetracker.sms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class SmsIngestRequest {

    @NotNull(message = "messages list is required")
    @Size(max = 200, message = "Maximum 200 messages per request")
    private List<SmsMessageDto> messages;

    public SmsIngestRequest() {
    }

    public SmsIngestRequest(List<SmsMessageDto> messages) {
        this.messages = messages;
    }

    public List<SmsMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<SmsMessageDto> messages) {
        this.messages = messages;
    }
}
