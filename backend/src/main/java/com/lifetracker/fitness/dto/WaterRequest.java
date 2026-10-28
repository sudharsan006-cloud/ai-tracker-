package com.lifetracker.fitness.dto;

public class WaterRequest {
    private Integer amountMl;
    private String date;

    public WaterRequest() {
    }

    public WaterRequest(Integer amountMl, String date) {
        this.amountMl = amountMl;
        this.date = date;
    }

    public Integer getAmountMl() { return amountMl; }
    public void setAmountMl(Integer amountMl) { this.amountMl = amountMl; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
}
