package com.lifetracker.fitness.dto;

public class RangeDayResponse {
    private String date;
    private int steps;
    private int waterMl;
    private int caloriesIn;
    private int caloriesBurnedEst;
    private int workoutCount;

    public RangeDayResponse() {
    }

    public RangeDayResponse(String date, int steps, int waterMl, int caloriesIn, int caloriesBurnedEst, int workoutCount) {
        this.date = date;
        this.steps = steps;
        this.waterMl = waterMl;
        this.caloriesIn = caloriesIn;
        this.caloriesBurnedEst = caloriesBurnedEst;
        this.workoutCount = workoutCount;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getSteps() { return steps; }
    public void setSteps(int steps) { this.steps = steps; }

    public int getWaterMl() { return waterMl; }
    public void setWaterMl(int waterMl) { this.waterMl = waterMl; }

    public int getCaloriesIn() { return caloriesIn; }
    public void setCaloriesIn(int caloriesIn) { this.caloriesIn = caloriesIn; }

    public int getCaloriesBurnedEst() { return caloriesBurnedEst; }
    public void setCaloriesBurnedEst(int caloriesBurnedEst) { this.caloriesBurnedEst = caloriesBurnedEst; }

    public int getWorkoutCount() { return workoutCount; }
    public void setWorkoutCount(int workoutCount) { this.workoutCount = workoutCount; }
}
