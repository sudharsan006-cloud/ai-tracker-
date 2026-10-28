package com.lifetracker.sms.dto;

public class SmsIngestResponse {

    private int received;
    private int created;
    private int queued;
    private int ignored;
    private int duplicates;

    public SmsIngestResponse() {
    }

    public SmsIngestResponse(int received, int created, int queued, int ignored, int duplicates) {
        this.received = received;
        this.created = created;
        this.queued = queued;
        this.ignored = ignored;
        this.duplicates = duplicates;
    }

    public int getReceived() {
        return received;
    }

    public void setReceived(int received) {
        this.received = received;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }

    public int getQueued() {
        return queued;
    }

    public void setQueued(int queued) {
        this.queued = queued;
    }

    public int getIgnored() {
        return ignored;
    }

    public void setIgnored(int ignored) {
        this.ignored = ignored;
    }

    public int getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(int duplicates) {
        this.duplicates = duplicates;
    }
}
