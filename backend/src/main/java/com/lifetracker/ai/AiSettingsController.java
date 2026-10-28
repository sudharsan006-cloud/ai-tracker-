package com.lifetracker.ai;

import com.lifetracker.ai.dto.AiSettingsDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/settings")
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;

    public AiSettingsController(AiSettingsService aiSettingsService) {
        this.aiSettingsService = aiSettingsService;
    }

    @GetMapping
    public ResponseEntity<AiSettingsDto> getSettings(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(aiSettingsService.getSettings(userId));
    }

    @PutMapping
    public ResponseEntity<AiSettingsDto> updateSettings(
            @AuthenticationPrincipal UUID userId,
            @RequestBody AiSettingsDto request) {
        return ResponseEntity.ok(aiSettingsService.updateSettings(userId, request));
    }
}
