package com.lifetracker.statement.dto;

public class CommitResponse {
    private int created;
    private int skipped;

    public CommitResponse() {
    }

    public CommitResponse(int created, int skipped) {
        this.created = created;
        this.skipped = skipped;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }
}
