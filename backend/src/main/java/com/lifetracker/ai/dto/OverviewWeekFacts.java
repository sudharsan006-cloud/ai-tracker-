package com.lifetracker.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class OverviewWeekFacts {
    private String kind = "overview_week";
    private String periodLabel;
    private int periodDays;
    private String currency;
    private String currencySymbol;
    private int transactionCount;
    private String spentLast7Days;
    private TopSpendingCategoryFact topSpendingCategory;
    private int daysWithFitnessData;
    private int averageSteps;
    private int averageWaterMl;
    private int workoutCount;
    private MoodOverviewFact mood; // null unless include_mood is true and entries > 0

    public OverviewWeekFacts() {}

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

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public String getSpentLast7Days() {
        return spentLast7Days;
    }

    public void setSpentLast7Days(String spentLast7Days) {
        this.spentLast7Days = spentLast7Days;
    }

    public TopSpendingCategoryFact getTopSpendingCategory() {
        return topSpendingCategory;
    }

    public void setTopSpendingCategory(TopSpendingCategoryFact topSpendingCategory) {
        this.topSpendingCategory = topSpendingCategory;
    }

    public int getDaysWithFitnessData() {
        return daysWithFitnessData;
    }

    public void setDaysWithFitnessData(int daysWithFitnessData) {
        this.daysWithFitnessData = daysWithFitnessData;
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

    public int getWorkoutCount() {
        return workoutCount;
    }

    public void setWorkoutCount(int workoutCount) {
        this.workoutCount = workoutCount;
    }

    public MoodOverviewFact getMood() {
        return mood;
    }

    public void setMood(MoodOverviewFact mood) {
        this.mood = mood;
    }
}
