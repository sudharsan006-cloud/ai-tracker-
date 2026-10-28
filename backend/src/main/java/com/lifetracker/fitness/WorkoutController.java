package com.lifetracker.fitness;

import com.lifetracker.fitness.dto.WorkoutCreateRequest;
import com.lifetracker.fitness.dto.WorkoutPageResponse;
import com.lifetracker.fitness.dto.WorkoutResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workouts")
public class WorkoutController {

    private final FitnessService fitnessService;

    public WorkoutController(FitnessService fitnessService) {
        this.fitnessService = fitnessService;
    }

    @PostMapping
    public ResponseEntity<WorkoutResponse> createWorkout(
            @AuthenticationPrincipal UUID userId,
            @RequestBody WorkoutCreateRequest request) {
        return ResponseEntity.ok(fitnessService.createWorkout(userId, request));
    }

    @GetMapping
    public ResponseEntity<WorkoutPageResponse> getWorkouts(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(fitnessService.getWorkouts(userId, from, to, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkoutResponse> getWorkout(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(fitnessService.getWorkout(userId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkoutResponse> updateWorkout(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody WorkoutCreateRequest request) {
        return ResponseEntity.ok(fitnessService.updateWorkout(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkout(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        fitnessService.deleteWorkout(userId, id);
        return ResponseEntity.noContent().build();
    }
}
