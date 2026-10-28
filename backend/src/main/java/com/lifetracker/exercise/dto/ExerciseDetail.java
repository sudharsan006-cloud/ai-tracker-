package com.lifetracker.exercise.dto;

import java.util.List;
import java.util.UUID;

public class ExerciseDetail {
    private UUID id;
    private String name;
    private String level;
    private String equipment;
    private String category;
    private String force;
    private String mechanic;
    private List<String> primaryMuscles;
    private List<String> secondaryMuscles;
    private List<String> instructions;
    private List<String> imageUrls;
    private boolean isFavorite;

    public ExerciseDetail() {
    }

    public ExerciseDetail(UUID id, String name, String level, String equipment, String category,
                          String force, String mechanic, List<String> primaryMuscles,
                          List<String> secondaryMuscles, List<String> instructions,
                          List<String> imageUrls, boolean isFavorite) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.equipment = equipment;
        this.category = category;
        this.force = force;
        this.mechanic = mechanic;
        this.primaryMuscles = primaryMuscles;
        this.secondaryMuscles = secondaryMuscles;
        this.instructions = instructions;
        this.imageUrls = imageUrls;
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

    public String getForce() { return force; }
    public void setForce(String force) { this.force = force; }

    public String getMechanic() { return mechanic; }
    public void setMechanic(String mechanic) { this.mechanic = mechanic; }

    public List<String> getPrimaryMuscles() { return primaryMuscles; }
    public void setPrimaryMuscles(List<String> primaryMuscles) { this.primaryMuscles = primaryMuscles; }

    public List<String> getSecondaryMuscles() { return secondaryMuscles; }
    public void setSecondaryMuscles(List<String> secondaryMuscles) { this.secondaryMuscles = secondaryMuscles; }

    public List<String> getInstructions() { return instructions; }
    public void setInstructions(List<String> instructions) { this.instructions = instructions; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public boolean getIsFavorite() { return isFavorite; }
    public void setIsFavorite(boolean isFavorite) { this.isFavorite = isFavorite; }
}
