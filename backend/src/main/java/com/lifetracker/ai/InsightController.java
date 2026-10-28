package com.lifetracker.ai;

import com.lifetracker.ai.dto.GenerateInsightResponse;
import com.lifetracker.ai.dto.InsightResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping
    public ResponseEntity<InsightResponse> getInsight(
            @AuthenticationPrincipal UUID userId,
            @RequestParam String kind) {
        return ResponseEntity.ok(insightService.getInsight(userId, kind));
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateInsightResponse> generateInsight(
            @AuthenticationPrincipal UUID userId,
            @RequestParam String kind) {
        return ResponseEntity.ok(insightService.generateInsight(userId, kind));
    }
}
