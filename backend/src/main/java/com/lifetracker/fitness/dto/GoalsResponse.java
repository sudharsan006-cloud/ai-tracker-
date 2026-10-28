package com.lifetracker.fitness.dto;

public class GoalsResponse {
    private int stepsGoal;
    private int waterGoalMl;
    private Integer calorieGoal;

    public GoalsResponse() {
    }

    public GoalsResponse(int stepsGoal, int waterGoalMl, Integer calorieGoal) {
        this.stepsGoal = stepsGoal;
        this.waterGoalMl = waterGoalMl;
        this.calorieGoal = calorieGoal;
    }

    public int getStepsGoal() { return stepsGoal; }
    public void setStepsGoal(int stepsGoal) { this.stepsGoal = stepsGoal; }

    public int getWaterGoalMl() { return waterGoalMl; }
    public void setWaterGoalMl(int waterGoalMl) { this.waterGoalMl = waterGoalMl; }

    public Integer getCalorieGoal() { return calorieGoal; }
    public void setCalorieGoal(Integer calorieGoal) { this.calorieGoal = calorieGoal; }
}
