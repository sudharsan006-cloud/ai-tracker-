package com.lifetracker.fitness;

import com.lifetracker.fitness.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fitness")
public class FitnessController {

    private final FitnessService fitnessService;

    public FitnessController(FitnessService fitnessService) {
        this.fitnessService = fitnessService;
    }

    @GetMapping("/goals")
    public ResponseEntity<GoalsResponse> getGoals(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(fitnessService.getGoals(userId));
    }

    @PutMapping("/goals")
    public ResponseEntity<GoalsResponse> putGoals(
            @AuthenticationPrincipal UUID userId,
            @RequestBody GoalsRequest request) {
        return ResponseEntity.ok(fitnessService.putGoals(userId, request));
    }

    @PutMapping("/daily/steps")
    public ResponseEntity<StepsResponse> updateSteps(
            @AuthenticationPrincipal UUID userId,
            @RequestBody StepsRequest request) {
        return ResponseEntity.ok(fitnessService.updateSteps(userId, request));
    }

    @PostMapping("/water")
    public ResponseEntity<WaterEntryResponse> addWater(
            @AuthenticationPrincipal UUID userId,
            @RequestBody WaterRequest request) {
        return ResponseEntity.ok(fitnessService.addWater(userId, request));
    }

    @GetMapping("/water")
    public ResponseEntity<List<WaterEntryResponse>> getWater(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(fitnessService.getWaterEntries(userId, date));
    }

    @DeleteMapping("/water/{id}")
    public ResponseEntity<Void> deleteWater(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        fitnessService.deleteWaterEntry(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/calories")
    public ResponseEntity<CalorieEntryResponse> addCalories(
            @AuthenticationPrincipal UUID userId,
            @RequestBody CalorieRequest request) {
        return ResponseEntity.ok(fitnessService.addCalorie(userId, request));
    }

    @GetMapping("/calories")
    public ResponseEntity<List<CalorieEntryResponse>> getCalories(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(fitnessService.getCalorieEntries(userId, date));
    }

    @DeleteMapping("/calories/{id}")
    public ResponseEntity<Void> deleteCalories(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        fitnessService.deleteCalorieEntry(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/daily")
    public ResponseEntity<DailySummaryResponse> getDailySummary(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(fitnessService.getDailySummary(userId, date));
    }

    @GetMapping("/range")
    public ResponseEntity<RangeResponse> getRange(
            @AuthenticationPrincipal UUID userId,
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(fitnessService.getRange(userId, from, to));
    }
}
