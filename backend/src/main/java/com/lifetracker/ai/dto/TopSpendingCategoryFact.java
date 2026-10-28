package com.lifetracker.ai.dto;

public class TopSpendingCategoryFact {
    private String name;
    private String spent;

    public TopSpendingCategoryFact() {}

    public TopSpendingCategoryFact(String name, String spent) {
        this.name = name;
        this.spent = spent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpent() {
        return spent;
    }

    public void setSpent(String spent) {
        this.spent = spent;
    }
}
