package com.lifetracker.mood;

import com.lifetracker.mood.dto.MoodNudgeResponse;
import com.lifetracker.mood.dto.MoodRequest;
import com.lifetracker.mood.dto.MoodResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mood")
public class MoodController {

    private final MoodService moodService;

    public MoodController(MoodService moodService) {
        this.moodService = moodService;
    }

    @PutMapping
    public ResponseEntity<MoodResponse> putMood(
            @AuthenticationPrincipal UUID userId,
            @RequestBody MoodRequest request) {
        return ResponseEntity.ok(moodService.putMood(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<MoodResponse>> getMood(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(moodService.getMoodEntries(userId, from, to));
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> deleteMood(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String date) {
        moodService.deleteMood(userId, date);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/nudge")
    public ResponseEntity<MoodNudgeResponse> getNudge(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(moodService.getNudge(userId));
    }

    @PostMapping("/nudge/dismiss")
    public ResponseEntity<Void> dismissNudge(
            @AuthenticationPrincipal UUID userId) {
        moodService.dismissNudge(userId);
        return ResponseEntity.noContent().build();
    }
}
