package com.lifetracker.ai.client;

public class GeminiResult {
    public final int httpStatus;       // 0 if no response was received (network error / timeout)
    public final String text;          // the answer text, or null
    public final String blockReason;   // promptFeedback.blockReason or null
    public final String finishReason;  // candidates[0].finishReason or null

    public GeminiResult(int httpStatus, String text, String blockReason, String finishReason) {
        this.httpStatus = httpStatus;
        this.text = text;
        this.blockReason = blockReason;
        this.finishReason = finishReason;
    }
}
