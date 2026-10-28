package com.lifetracker.fitness.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class WorkoutEntryResponse {
    private UUID id;
    private int position;
    private UUID exerciseId;
    private String exerciseName;
    private String thumbnailUrl;
    private int sets;
    private Integer reps;
    private Integer durationSeconds;
    private BigDecimal weightKg;

    public WorkoutEntryResponse() {
    }

    public WorkoutEntryResponse(UUID id, int position, UUID exerciseId, String exerciseName,
                                String thumbnailUrl, int sets, Integer reps,
                                Integer durationSeconds, BigDecimal weightKg) {
        this.id = id;
        this.position = position;
        this.exerciseId = exerciseId;
        this.exerciseName = exerciseName;
        this.thumbnailUrl = thumbnailUrl;
        this.sets = sets;
        this.reps = reps;
        this.durationSeconds = durationSeconds;
        this.weightKg = weightKg;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public UUID getExerciseId() { return exerciseId; }
    public void setExerciseId(UUID exerciseId) { this.exerciseId = exerciseId; }

    public String getExerciseName() { return exerciseName; }
    public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public int getSets() { return sets; }
    public void setSets(int sets) { this.sets = sets; }

    public Integer getReps() { return reps; }
    public void setReps(Integer reps) { this.reps = reps; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
}
