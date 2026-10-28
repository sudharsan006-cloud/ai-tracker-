package com.lifetracker.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class FitnessWeekFacts {
    private String kind = "fitness_week";
    private String periodLabel;
    private int periodDays;
    private int daysWithData;
    private int stepsGoal;
    private int waterGoalMl;
    private int averageSteps;
    private int averageWaterMl;
    private int averageCaloriesIn;
    private int daysStepsGoalMet;
    private int daysWaterGoalMet;
    private int workoutCount;
    private int workoutTotalMinutes;
    private int caloriesBurnedEstTotal;
    private BestStepsDay bestStepsDay; // null if all days have 0 steps

    public FitnessWeekFacts() {}

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public void setPeriodLabel(String periodLabel) {
        this.periodLabel = periodLabel;
    }

    public int getPeriodDays() {
        return periodDays;
    }

    public void setPeriodDays(int periodDays) {
        this.periodDays = periodDays;
    }

    public int getDaysWithData() {
        return daysWithData;
    }

    public void setDaysWithData(int daysWithData) {
        this.daysWithData = daysWithData;
    }

    public int getStepsGoal() {
        return stepsGoal;
    }

    public void setStepsGoal(int stepsGoal) {
        this.stepsGoal = stepsGoal;
    }

    public int getWaterGoalMl() {
        return waterGoalMl;
    }

    public void setWaterGoalMl(int waterGoalMl) {
        this.waterGoalMl = waterGoalMl;
    }

    public int getAverageSteps() {
        return averageSteps;
    }

    public void setAverageSteps(int averageSteps) {
        this.averageSteps = averageSteps;
    }

    public int getAverageWaterMl() {
        return averageWaterMl;
    }

    public void setAverageWaterMl(int averageWaterMl) {
        this.averageWaterMl = averageWaterMl;
    }

    public int getAverageCaloriesIn() {
        return averageCaloriesIn;
    }

    public void setAverageCaloriesIn(int averageCaloriesIn) {
        this.averageCaloriesIn = averageCaloriesIn;
    }

    public int getDaysStepsGoalMet() {
        return daysStepsGoalMet;
    }

    public void setDaysStepsGoalMet(int daysStepsGoalMet) {
        this.daysStepsGoalMet = daysStepsGoalMet;
    }

    public int getDaysWaterGoalMet() {
        return daysWaterGoalMet;
    }

    public void setDaysWaterGoalMet(int daysWaterGoalMet) {
        this.daysWaterGoalMet = daysWaterGoalMet;
    }

    public int getWorkoutCount() {
        return workoutCount;
    }

    public void setWorkoutCount(int workoutCount) {
        this.workoutCount = workoutCount;
    }

    public int getWorkoutTotalMinutes() {
        return workoutTotalMinutes;
    }

    public void setWorkoutTotalMinutes(int workoutTotalMinutes) {
        this.workoutTotalMinutes = workoutTotalMinutes;
    }

    public int getCaloriesBurnedEstTotal() {
        return caloriesBurnedEstTotal;
    }

    public void setCaloriesBurnedEstTotal(int caloriesBurnedEstTotal) {
        this.caloriesBurnedEstTotal = caloriesBurnedEstTotal;
    }

    public BestStepsDay getBestStepsDay() {
        return bestStepsDay;
    }

    public void setBestStepsDay(BestStepsDay bestStepsDay) {
        this.bestStepsDay = bestStepsDay;
    }
}
