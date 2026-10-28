package com.lifetracker.ai.dto;

public class TopCategoryFact {
    private String name;
    private String spent;
    private String sharePercent;

    public TopCategoryFact() {}

    public TopCategoryFact(String name, String spent, String sharePercent) {
        this.name = name;
        this.spent = spent;
        this.sharePercent = sharePercent;
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

    public String getSharePercent() {
        return sharePercent;
    }

    public void setSharePercent(String sharePercent) {
        this.sharePercent = sharePercent;
    }
}
