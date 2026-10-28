package com.lifetracker.mood.dto;

public class MoodRequest {
    private String date;
    private Short score;
    private String note;

    public MoodRequest() {}

    public MoodRequest(String date, Short score, String note) {
        this.date = date;
        this.score = score;
        this.note = note;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Short getScore() {
        return score;
    }

    public void setScore(Short score) {
        this.score = score;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
