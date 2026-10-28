package com.lifetracker.fitness.dto;

import java.util.List;
import java.util.UUID;

public class WorkoutResponse {
    private UUID id;
    private String date;
    private String name;
    private int durationMin;
    private String intensity;
    private Integer caloriesBurnedEst;
    private String notes;
    private List<WorkoutEntryResponse> entries;

    public WorkoutResponse() {
    }

    public WorkoutResponse(UUID id, String date, String name, int durationMin, String intensity,
                           Integer caloriesBurnedEst, String notes, List<WorkoutEntryResponse> entries) {
        this.id = id;
        this.date = date;
        this.name = name;
        this.durationMin = durationMin;
        this.intensity = intensity;
        this.caloriesBurnedEst = caloriesBurnedEst;
        this.notes = notes;
        this.entries = entries;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDurationMin() { return durationMin; }
    public void setDurationMin(int durationMin) { this.durationMin = durationMin; }

    public String getIntensity() { return intensity; }
    public void setIntensity(String intensity) { this.intensity = intensity; }

    public Integer getCaloriesBurnedEst() { return caloriesBurnedEst; }
    public void setCaloriesBurnedEst(Integer caloriesBurnedEst) { this.caloriesBurnedEst = caloriesBurnedEst; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<WorkoutEntryResponse> getEntries() { return entries; }
    public void setEntries(List<WorkoutEntryResponse> entries) { this.entries = entries; }
}
