package com.lifetracker.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class SpendingMonthFacts {
    private String kind = "spending_month";
    private String periodLabel;
    private String currency;
    private String currencySymbol;
    private int daysElapsed;
    private int daysInMonth;
    private int transactionCount;
    private String totalSpent;
    private String totalIncome;
    private String previousPeriodSpent;
    private String changeAmount;
    private String changePercent; // null when previousPeriodSpent is 0
    private List<TopCategoryFact> topCategories;
    private List<BudgetFact> budgets;

    public SpendingMonthFacts() {}

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

    public int getDaysElapsed() {
        return daysElapsed;
    }

    public void setDaysElapsed(int daysElapsed) {
        this.daysElapsed = daysElapsed;
    }

    public int getDaysInMonth() {
        return daysInMonth;
    }

    public void setDaysInMonth(int daysInMonth) {
        this.daysInMonth = daysInMonth;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public String getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(String totalSpent) {
        this.totalSpent = totalSpent;
    }

    public String getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(String totalIncome) {
        this.totalIncome = totalIncome;
    }

    public String getPreviousPeriodSpent() {
        return previousPeriodSpent;
    }

    public void setPreviousPeriodSpent(String previousPeriodSpent) {
        this.previousPeriodSpent = previousPeriodSpent;
    }

    public String getChangeAmount() {
        return changeAmount;
    }

    public void setChangeAmount(String changeAmount) {
        this.changeAmount = changeAmount;
    }

    public String getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(String changePercent) {
        this.changePercent = changePercent;
    }

    public List<TopCategoryFact> getTopCategories() {
        return topCategories;
    }

    public void setTopCategories(List<TopCategoryFact> topCategories) {
        this.topCategories = topCategories;
    }

    public List<BudgetFact> getBudgets() {
        return budgets;
    }

    public void setBudgets(List<BudgetFact> budgets) {
        this.budgets = budgets;
    }
}
