package com.lifetracker.statement;

public class StatementException extends RuntimeException {
    private final String errorCode;

    public StatementException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public StatementException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public StatementException(String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
