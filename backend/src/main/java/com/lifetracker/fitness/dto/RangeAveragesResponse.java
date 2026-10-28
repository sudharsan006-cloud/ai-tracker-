package com.lifetracker.fitness.dto;

public class RangeAveragesResponse {
    private int steps;
    private int waterMl;
    private int caloriesIn;

    public RangeAveragesResponse() {
    }

    public RangeAveragesResponse(int steps, int waterMl, int caloriesIn) {
        this.steps = steps;
        this.waterMl = waterMl;
        this.caloriesIn = caloriesIn;
    }

    public int getSteps() { return steps; }
    public void setSteps(int steps) { this.steps = steps; }

    public int getWaterMl() { return waterMl; }
    public void setWaterMl(int waterMl) { this.waterMl = waterMl; }

    public int getCaloriesIn() { return caloriesIn; }
    public void setCaloriesIn(int caloriesIn) { this.caloriesIn = caloriesIn; }
}
