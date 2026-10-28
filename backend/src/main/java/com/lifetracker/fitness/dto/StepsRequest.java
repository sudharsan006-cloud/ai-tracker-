package com.lifetracker.fitness.dto;

public class StepsRequest {
    private String date;
    private Integer steps;
    private String source;

    public StepsRequest() {
    }

    public StepsRequest(String date, Integer steps, String source) {
        this.date = date;
        this.steps = steps;
        this.source = source;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public Integer getSteps() { return steps; }
    public void setSteps(Integer steps) { this.steps = steps; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
