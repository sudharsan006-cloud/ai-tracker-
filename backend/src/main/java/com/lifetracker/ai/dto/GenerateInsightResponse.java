package com.lifetracker.ai.dto;

public class GenerateInsightResponse {
    private InsightDto insight;
    private String fallbackReason;
    private int callsRemainingToday;

    public GenerateInsightResponse() {}

    public GenerateInsightResponse(InsightDto insight, String fallbackReason, int callsRemainingToday) {
        this.insight = insight;
        this.fallbackReason = fallbackReason;
        this.callsRemainingToday = callsRemainingToday;
    }

    public InsightDto getInsight() {
        return insight;
    }

    public void setInsight(InsightDto insight) {
        this.insight = insight;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public int getCallsRemainingToday() {
        return callsRemainingToday;
    }

    public void setCallsRemainingToday(int callsRemainingToday) {
        this.callsRemainingToday = callsRemainingToday;
    }
}
