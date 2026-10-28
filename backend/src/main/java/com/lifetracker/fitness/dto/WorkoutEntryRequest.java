package com.lifetracker.fitness.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class WorkoutEntryRequest {
    private UUID exerciseId;
    private String customName;
    private Integer sets;
    private Integer reps;
    private Integer durationSeconds;
    private BigDecimal weightKg;

    public WorkoutEntryRequest() {
    }

    public WorkoutEntryRequest(UUID exerciseId, String customName, Integer sets, Integer reps,
                               Integer durationSeconds, BigDecimal weightKg) {
        this.exerciseId = exerciseId;
        this.customName = customName;
        this.sets = sets;
        this.reps = reps;
        this.durationSeconds = durationSeconds;
        this.weightKg = weightKg;
    }

    public UUID getExerciseId() { return exerciseId; }
    public void setExerciseId(UUID exerciseId) { this.exerciseId = exerciseId; }

    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }

    public Integer getSets() { return sets; }
    public void setSets(Integer sets) { this.sets = sets; }

    public Integer getReps() { return reps; }
    public void setReps(Integer reps) { this.reps = reps; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
}
