package com.lifetracker.fitness.dto;

import java.time.Instant;
import java.util.UUID;

public class WaterEntryResponse {
    private UUID id;
    private String date;
    private int amountMl;
    private Instant loggedAt;

    public WaterEntryResponse() {
    }

    public WaterEntryResponse(UUID id, String date, int amountMl, Instant loggedAt) {
        this.id = id;
        this.date = date;
        this.amountMl = amountMl;
        this.loggedAt = loggedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getAmountMl() { return amountMl; }
    public void setAmountMl(int amountMl) { this.amountMl = amountMl; }

    public Instant getLoggedAt() { return loggedAt; }
    public void setLoggedAt(Instant loggedAt) { this.loggedAt = loggedAt; }
}
