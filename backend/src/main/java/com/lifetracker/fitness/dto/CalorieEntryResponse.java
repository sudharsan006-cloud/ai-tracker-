package com.lifetracker.fitness.dto;

import java.time.Instant;
import java.util.UUID;

public class CalorieEntryResponse {
    private UUID id;
    private String date;
    private int kcal;
    private String label;
    private Instant loggedAt;

    public CalorieEntryResponse() {
    }

    public CalorieEntryResponse(UUID id, String date, int kcal, String label, Instant loggedAt) {
        this.id = id;
        this.date = date;
        this.kcal = kcal;
        this.label = label;
        this.loggedAt = loggedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getKcal() { return kcal; }
    public void setKcal(int kcal) { this.kcal = kcal; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Instant getLoggedAt() { return loggedAt; }
    public void setLoggedAt(Instant loggedAt) { this.loggedAt = loggedAt; }
}
