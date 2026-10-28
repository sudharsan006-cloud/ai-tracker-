package com.lifetracker.fitness.dto;

public class CalorieRequest {
    private Integer kcal;
    private String label;
    private String date;

    public CalorieRequest() {
    }

    public CalorieRequest(Integer kcal, String label, String date) {
        this.kcal = kcal;
        this.label = label;
        this.date = date;
    }

    public Integer getKcal() { return kcal; }
    public void setKcal(Integer kcal) { this.kcal = kcal; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
}
