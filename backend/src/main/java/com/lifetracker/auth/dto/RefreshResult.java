package com.lifetracker.auth.dto;

public class RefreshResult {
    private final boolean success;
    private final AuthResponse response;

    private RefreshResult(boolean success, AuthResponse response) {
        this.success = success;
        this.response = response;
    }

    public static RefreshResult ok(AuthResponse response) {
        return new RefreshResult(true, response);
    }

    public static RefreshResult rejected() {
        return new RefreshResult(false, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public AuthResponse getResponse() {
        return response;
    }
}
