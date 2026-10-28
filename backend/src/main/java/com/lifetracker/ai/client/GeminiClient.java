package com.lifetracker.ai.client;

public interface GeminiClient {
    GeminiResult generate(String systemInstruction, String userText);
}
