package com.lifetracker.ai.dto;

public class BudgetFact {
    private String label;
    private String amount;
    private String spent;
    private String percentUsed;
    private boolean overBudget;

    public BudgetFact() {}

    public BudgetFact(String label, String amount, String spent, String percentUsed, boolean overBudget) {
        this.label = label;
        this.amount = amount;
        this.spent = spent;
        this.percentUsed = percentUsed;
        this.overBudget = overBudget;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getSpent() {
        return spent;
    }

    public void setSpent(String spent) {
        this.spent = spent;
    }

    public String getPercentUsed() {
        return percentUsed;
    }

    public void setPercentUsed(String percentUsed) {
        this.percentUsed = percentUsed;
    }

    public boolean isOverBudget() {
        return overBudget;
    }

    public void setOverBudget(boolean overBudget) {
        this.overBudget = overBudget;
    }
}
