package com.lifetracker.exercise.dto;

import java.util.List;
import java.util.UUID;

public class ExerciseSummary {
    private UUID id;
    private String name;
    private String level;
    private String equipment;
    private String category;
    private List<String> primaryMuscles;
    private String thumbnailUrl;
    private boolean isFavorite;

    public ExerciseSummary() {
    }

    public ExerciseSummary(UUID id, String name, String level, String equipment, String category,
                           List<String> primaryMuscles, String thumbnailUrl, boolean isFavorite) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.equipment = equipment;
        this.category = category;
        this.primaryMuscles = primaryMuscles;
        this.thumbnailUrl = thumbnailUrl;
        this.isFavorite = isFavorite;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getPrimaryMuscles() { return primaryMuscles; }
    public void setPrimaryMuscles(List<String> primaryMuscles) { this.primaryMuscles = primaryMuscles; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public boolean getIsFavorite() { return isFavorite; }
    public void setIsFavorite(boolean isFavorite) { this.isFavorite = isFavorite; }
}
