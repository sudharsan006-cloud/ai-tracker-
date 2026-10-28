package com.lifetracker.fitness.dto;

public class DailySummaryResponse {
    private String date;
    private int steps;
    private String stepsSource;
    private int stepsGoal;
    private int stepsPercent;

    private int waterMl;
    private int waterGoalMl;
    private int waterPercent;

    private int caloriesIn;
    private Integer calorieGoal;
    private Integer caloriesPercent;

    private int caloriesBurnedEst;
    private int workoutCount;
    private Integer bmrEstimate;

    public DailySummaryResponse() {
    }

    public DailySummaryResponse(String date, int steps, String stepsSource, int stepsGoal, int stepsPercent,
                                int waterMl, int waterGoalMl, int waterPercent,
                                int caloriesIn, Integer calorieGoal, Integer caloriesPercent,
                                int caloriesBurnedEst, int workoutCount, Integer bmrEstimate) {
        this.date = date;
        this.steps = steps;
        this.stepsSource = stepsSource;
        this.stepsGoal = stepsGoal;
        this.stepsPercent = stepsPercent;
        this.waterMl = waterMl;
        this.waterGoalMl = waterGoalMl;
        this.waterPercent = waterPercent;
        this.caloriesIn = caloriesIn;
        this.calorieGoal = calorieGoal;
        this.caloriesPercent = caloriesPercent;
        this.caloriesBurnedEst = caloriesBurnedEst;
        this.workoutCount = workoutCount;
        this.bmrEstimate = bmrEstimate;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getSteps() { return steps; }
    public void setSteps(int steps) { this.steps = steps; }

    public String getStepsSource() { return stepsSource; }
    public void setStepsSource(String stepsSource) { this.stepsSource = stepsSource; }

    public int getStepsGoal() { return stepsGoal; }
    public void setStepsGoal(int stepsGoal) { this.stepsGoal = stepsGoal; }

    public int getStepsPercent() { return stepsPercent; }
    public void setStepsPercent(int stepsPercent) { this.stepsPercent = stepsPercent; }

    public int getWaterMl() { return waterMl; }
    public void setWaterMl(int waterMl) { this.waterMl = waterMl; }

    public int getWaterGoalMl() { return waterGoalMl; }
    public void setWaterGoalMl(int waterGoalMl) { this.waterGoalMl = waterGoalMl; }

    public int getWaterPercent() { return waterPercent; }
    public void setWaterPercent(int waterPercent) { this.waterPercent = waterPercent; }

    public int getCaloriesIn() { return caloriesIn; }
    public void setCaloriesIn(int caloriesIn) { this.caloriesIn = caloriesIn; }

    public Integer getCalorieGoal() { return calorieGoal; }
    public void setCalorieGoal(Integer calorieGoal) { this.calorieGoal = calorieGoal; }

    public Integer getCaloriesPercent() { return caloriesPercent; }
    public void setCaloriesPercent(Integer caloriesPercent) { this.caloriesPercent = caloriesPercent; }

    public int getCaloriesBurnedEst() { return caloriesBurnedEst; }
    public void setCaloriesBurnedEst(int caloriesBurnedEst) { this.caloriesBurnedEst = caloriesBurnedEst; }

    public int getWorkoutCount() { return workoutCount; }
    public void setWorkoutCount(int workoutCount) { this.workoutCount = workoutCount; }

    public Integer getBmrEstimate() { return bmrEstimate; }
    public void setBmrEstimate(Integer bmrEstimate) { this.bmrEstimate = bmrEstimate; }
}
