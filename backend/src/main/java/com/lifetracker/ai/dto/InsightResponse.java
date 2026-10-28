package com.lifetracker.ai.dto;

import java.time.LocalDate;

public class InsightResponse {
    private String kind;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Object facts;
    private boolean hasData;
    private boolean aiEnabled;
    private boolean aiConfigured;
    private int callsRemainingToday;
    private InsightDto insight;

    public InsightResponse() {}

    public InsightResponse(String kind, LocalDate periodStart, LocalDate periodEnd, Object facts,
                           boolean hasData, boolean aiEnabled, boolean aiConfigured,
                           int callsRemainingToday, InsightDto insight) {
        this.kind = kind;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.facts = facts;
        this.hasData = hasData;
        this.aiEnabled = aiEnabled;
        this.aiConfigured = aiConfigured;
        this.callsRemainingToday = callsRemainingToday;
        this.insight = insight;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Object getFacts() {
        return facts;
    }

    public void setFacts(Object facts) {
        this.facts = facts;
    }

    public boolean isHasData() {
        return hasData;
    }

    public void setHasData(boolean hasData) {
        this.hasData = hasData;
    }

    public boolean isAiEnabled() {
        return aiEnabled;
    }

    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    public boolean isAiConfigured() {
        return aiConfigured;
    }

    public void setAiConfigured(boolean aiConfigured) {
        this.aiConfigured = aiConfigured;
    }

    public int getCallsRemainingToday() {
        return callsRemainingToday;
    }

    public void setCallsRemainingToday(int callsRemainingToday) {
        this.callsRemainingToday = callsRemainingToday;
    }

    public InsightDto getInsight() {
        return insight;
    }

    public void setInsight(InsightDto insight) {
        this.insight = insight;
    }
}
