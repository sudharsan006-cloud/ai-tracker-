package com.lifetracker.exercise;

import com.lifetracker.exercise.dto.ExerciseDetail;
import com.lifetracker.exercise.dto.ExerciseFiltersResponse;
import com.lifetracker.exercise.dto.ExercisePageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping
    public ResponseEntity<ExercisePageResponse> getExercises(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String muscle,
            @RequestParam(required = false) String equipment,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean favoritesOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(exerciseService.getExercises(q, muscle, equipment, level, category, favoritesOnly, page, size, userId));
    }

    @GetMapping("/filters")
    public ResponseEntity<ExerciseFiltersResponse> getFilters() {
        return ResponseEntity.ok(exerciseService.getFilters());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExerciseDetail> getExercise(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(exerciseService.getExercise(id, userId));
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<Void> addFavorite(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        exerciseService.addFavorite(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/favorite")
    public ResponseEntity<Void> removeFavorite(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        exerciseService.removeFavorite(id, userId);
        return ResponseEntity.noContent().build();
    }
}
