package com.lifetracker.ai;

import com.lifetracker.ai.client.GeminiClient;
import com.lifetracker.ai.client.GeminiResult;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeGeminiClient implements GeminiClient {

    private final Queue<GeminiResult> queuedResponses = new LinkedList<>();
    private final AtomicInteger callCount = new AtomicInteger(0);
    private GeminiResult defaultResponse = new GeminiResult(200, "Tracked numbers look steady.", null, "STOP");

    public void queueResponse(GeminiResult response) {
        queuedResponses.add(response);
    }

    public void setDefaultResponse(GeminiResult response) {
        this.defaultResponse = response;
    }

    public void reset() {
        queuedResponses.clear();
        callCount.set(0);
        defaultResponse = new GeminiResult(200, "Tracked numbers look steady.", null, "STOP");
    }

    public int getCallCount() {
        return callCount.get();
    }

    @Override
    public GeminiResult generate(String systemInstruction, String userText) {
        callCount.incrementAndGet();
        if (!queuedResponses.isEmpty()) {
            return queuedResponses.poll();
        }
        return defaultResponse;
    }
}
