package com.lifetracker.exercise.dto;

import java.util.List;

public class ExerciseFiltersResponse {
    private List<String> muscles;
    private List<String> equipment;
    private List<String> levels;
    private List<String> categories;

    public ExerciseFiltersResponse() {
    }

    public ExerciseFiltersResponse(List<String> muscles, List<String> equipment, List<String> levels, List<String> categories) {
        this.muscles = muscles;
        this.equipment = equipment;
        this.levels = levels;
        this.categories = categories;
    }

    public List<String> getMuscles() { return muscles; }
    public void setMuscles(List<String> muscles) { this.muscles = muscles; }

    public List<String> getEquipment() { return equipment; }
    public void setEquipment(List<String> equipment) { this.equipment = equipment; }

    public List<String> getLevels() { return levels; }
    public void setLevels(List<String> levels) { this.levels = levels; }

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }
}
