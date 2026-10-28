package com.lifetracker.fitness.dto;

public class StepsResponse {
    private String date;
    private int steps;
    private String stepsSource;

    public StepsResponse() {
    }

    public StepsResponse(String date, int steps, String stepsSource) {
        this.date = date;
        this.steps = steps;
        this.stepsSource = stepsSource;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getSteps() { return steps; }
    public void setSteps(int steps) { this.steps = steps; }

    public String getStepsSource() { return stepsSource; }
    public void setStepsSource(String stepsSource) { this.stepsSource = stepsSource; }
}
