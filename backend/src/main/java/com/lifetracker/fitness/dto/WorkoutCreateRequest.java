package com.lifetracker.fitness.dto;

import java.util.List;

public class WorkoutCreateRequest {
    private String date;
    private String name;
    private Integer durationMin;
    private String intensity;
    private String notes;
    private List<WorkoutEntryRequest> entries;

    public WorkoutCreateRequest() {
    }

    public WorkoutCreateRequest(String date, String name, Integer durationMin, String intensity,
                                String notes, List<WorkoutEntryRequest> entries) {
        this.date = date;
        this.name = name;
        this.durationMin = durationMin;
        this.intensity = intensity;
        this.notes = notes;
        this.entries = entries;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getDurationMin() { return durationMin; }
    public void setDurationMin(Integer durationMin) { this.durationMin = durationMin; }

    public String getIntensity() { return intensity; }
    public void setIntensity(String intensity) { this.intensity = intensity; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<WorkoutEntryRequest> getEntries() { return entries; }
    public void setEntries(List<WorkoutEntryRequest> entries) { this.entries = entries; }
}
