package com.lifetracker.mood.dto;

public class MoodResponse {
    private String date;
    private short score;
    private String note;

    public MoodResponse() {}

    public MoodResponse(String date, short score, String note) {
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

    public short getScore() {
        return score;
    }

    public void setScore(short score) {
        this.score = score;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
