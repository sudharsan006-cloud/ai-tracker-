package com.lifetracker.fitness.dto;

public class GoalsRequest {
    private Integer stepsGoal;
    private Integer waterGoalMl;
    private Integer calorieGoal;

    public GoalsRequest() {
    }

    public GoalsRequest(Integer stepsGoal, Integer waterGoalMl, Integer calorieGoal) {
        this.stepsGoal = stepsGoal;
        this.waterGoalMl = waterGoalMl;
        this.calorieGoal = calorieGoal;
    }

    public Integer getStepsGoal() { return stepsGoal; }
    public void setStepsGoal(Integer stepsGoal) { this.stepsGoal = stepsGoal; }

    public Integer getWaterGoalMl() { return waterGoalMl; }
    public void setWaterGoalMl(Integer waterGoalMl) { this.waterGoalMl = waterGoalMl; }

    public Integer getCalorieGoal() { return calorieGoal; }
    public void setCalorieGoal(Integer calorieGoal) { this.calorieGoal = calorieGoal; }
}
