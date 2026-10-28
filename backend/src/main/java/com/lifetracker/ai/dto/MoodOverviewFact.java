package com.lifetracker.ai.dto;

public class MoodOverviewFact {
    private int entries;
    private String averageScore;
    private int lowDays;

    public MoodOverviewFact() {}

    public MoodOverviewFact(int entries, String averageScore, int lowDays) {
        this.entries = entries;
        this.averageScore = averageScore;
        this.lowDays = lowDays;
    }

    public int getEntries() {
        return entries;
    }

    public void setEntries(int entries) {
        this.entries = entries;
    }

    public String getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(String averageScore) {
        this.averageScore = averageScore;
    }

    public int getLowDays() {
        return lowDays;
    }

    public void setLowDays(int lowDays) {
        this.lowDays = lowDays;
    }
}
