package com.lifetracker.ai.dto;

public class AiSettingsDto {
    private boolean aiEnabled;
    private boolean includeMood;

    public AiSettingsDto() {}

    public AiSettingsDto(boolean aiEnabled, boolean includeMood) {
        this.aiEnabled = aiEnabled;
        this.includeMood = includeMood;
    }

    public boolean isAiEnabled() {
        return aiEnabled;
    }

    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    public boolean isIncludeMood() {
        return includeMood;
    }

    public void setIncludeMood(boolean includeMood) {
        this.includeMood = includeMood;
    }
}
