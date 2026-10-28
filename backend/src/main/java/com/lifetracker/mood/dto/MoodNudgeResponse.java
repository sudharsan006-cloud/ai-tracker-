package com.lifetracker.mood.dto;

public class MoodNudgeResponse {
    private boolean show;
    private String message;

    public MoodNudgeResponse() {}

    public MoodNudgeResponse(boolean show, String message) {
        this.show = show;
        this.message = message;
    }

    public boolean isShow() {
        return show;
    }

    public void setShow(boolean show) {
        this.show = show;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
