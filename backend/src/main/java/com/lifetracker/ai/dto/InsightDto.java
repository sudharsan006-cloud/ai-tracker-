package com.lifetracker.ai.dto;

import java.time.Instant;

public class InsightDto {
    private String text;
    private Instant createdAt;
    private String model;
    private boolean stale;

    public InsightDto() {}

    public InsightDto(String text, Instant createdAt, String model, boolean stale) {
        this.text = text;
        this.createdAt = createdAt;
        this.model = model;
        this.stale = stale;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }
}
