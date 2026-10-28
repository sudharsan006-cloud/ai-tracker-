package com.lifetracker.ai.dto;

public class BestStepsDay {
    private String date;
    private int steps;

    public BestStepsDay() {}

    public BestStepsDay(String date, int steps) {
        this.date = date;
        this.steps = steps;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }
}
