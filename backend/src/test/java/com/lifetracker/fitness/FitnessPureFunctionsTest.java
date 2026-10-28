package com.lifetracker.fitness;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetracker.exercise.ExerciseImageUrlBuilder;
import com.lifetracker.exercise.ExerciseSeedItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class FitnessPureFunctionsTest {

    @Test
    @DisplayName("Test 1: bmrEstimate - male, female, other, nulls, and onDate usage")
    void testBmrEstimate() {
        LocalDate dob = LocalDate.of(1994, 1, 1);
        LocalDate onDate = LocalDate.of(2024, 1, 1); // exactly 30 years old

        // Male: 30 years, 80 kg, 180 cm -> 1780
        Integer maleBmr = BmrCalculator.calculate("male", BigDecimal.valueOf(80), BigDecimal.valueOf(180), dob, onDate);
        assertThat(maleBmr).isEqualTo(1780);

        // Female: same inputs -> 1614
        Integer femaleBmr = BmrCalculator.calculate("female", BigDecimal.valueOf(80), BigDecimal.valueOf(180), dob, onDate);
        assertThat(femaleBmr).isEqualTo(1614);

        // Sex 'other' or invalid -> null
        assertThat(BmrCalculator.calculate("other", BigDecimal.valueOf(80), BigDecimal.valueOf(180), dob, onDate)).isNull();
        assertThat(BmrCalculator.calculate("non-binary", BigDecimal.valueOf(80), BigDecimal.valueOf(180), dob, onDate)).isNull();

        // Any null input -> null
        assertThat(BmrCalculator.calculate(null, BigDecimal.valueOf(80), BigDecimal.valueOf(180), dob, onDate)).isNull();
        assertThat(BmrCalculator.calculate("male", null, BigDecimal.valueOf(180), dob, onDate)).isNull();
        assertThat(BmrCalculator.calculate("male", BigDecimal.valueOf(80), null, dob, onDate)).isNull();
        assertThat(BmrCalculator.calculate("male", BigDecimal.valueOf(80), BigDecimal.valueOf(180), null, onDate)).isNull();
        assertThat(BmrCalculator.calculate("male", BigDecimal.valueOf(80), BigDecimal.valueOf(180), dob, null)).isNull();

        // onDate changes age: onDate before birthday in same year (29 years old)
        LocalDate dayBefore30thBday = LocalDate.of(2023, 12, 31);
        Integer maleBmr29 = BmrCalculator.calculate("male", BigDecimal.valueOf(80), BigDecimal.valueOf(180), dob, dayBefore30thBday);
        // age 29: -5*29 = -145 instead of -150 -> base is 5 higher -> 1785
        assertThat(maleBmr29).isEqualTo(1785);
    }

    @Test
    @DisplayName("Test 2: caloriesBurnedEstimate - MET 5 70kg 60min -> 350; MET 3 80kg 30min -> 120; no weight -> null")
    void testCaloriesBurnedEstimate() {
        // MET 5, 70 kg, 60 min -> 350
        Integer est1 = WorkoutCalorieCalculator.calculate(BigDecimal.valueOf(5), BigDecimal.valueOf(70), 60);
        assertThat(est1).isEqualTo(350);

        // MET 3, 80 kg, 30 min -> 120
        Integer est2 = WorkoutCalorieCalculator.calculate(BigDecimal.valueOf(3), BigDecimal.valueOf(80), 30);
        assertThat(est2).isEqualTo(120);

        // No weight -> null
        assertThat(WorkoutCalorieCalculator.calculate(BigDecimal.valueOf(5), null, 60)).isNull();
        assertThat(WorkoutCalorieCalculator.calculate(null, BigDecimal.valueOf(70), 60)).isNull();
    }

    @Test
    @DisplayName("Test 3: Image URL builder - adds trailing slash, encodes segments, preserves slashes")
    void testImageUrlBuilder() {
        ExerciseImageUrlBuilder builderWithoutSlash = new ExerciseImageUrlBuilder("https://cdn.example.com/exercises");
        assertThat(builderWithoutSlash.getBaseUrl()).isEqualTo("https://cdn.example.com/exercises/");

        ExerciseImageUrlBuilder builderWithSlash = new ExerciseImageUrlBuilder("https://cdn.example.com/exercises/");
        assertThat(builderWithSlash.getBaseUrl()).isEqualTo("https://cdn.example.com/exercises/");

        List<String> paths = List.of(
                "Bench Press/0.jpg",
                "3/4 Sit-Up/1.jpg"
        );
        List<String> urls = builderWithSlash.imageUrls(paths);
        assertThat(urls).hasSize(2);
        assertThat(urls.get(0)).isEqualTo("https://cdn.example.com/exercises/Bench%20Press/0.jpg");
        assertThat(urls.get(1)).isEqualTo("https://cdn.example.com/exercises/3/4%20Sit-Up/1.jpg");

        // thumbnailUrl helper
        assertThat(builderWithSlash.thumbnailUrl(paths)).isEqualTo("https://cdn.example.com/exercises/Bench%20Press/0.jpg");
        assertThat(builderWithSlash.thumbnailUrl(List.of())).isNull();
    }

    @Test
    @DisplayName("Test 4: Seed file validation - valid JSON array, >= 150 entries, unique id, >=1 image, >=1 instruction, allowed equipment")
    void testSeedFileValidation() throws Exception {
        ClassPathResource resource = new ClassPathResource("seed/exercises.json");
        assertThat(resource.exists()).isTrue();

        ObjectMapper mapper = new ObjectMapper();
        List<ExerciseSeedItem> items;
        try (InputStream is = resource.getInputStream()) {
            items = mapper.readValue(is, new TypeReference<>() {});
        }

        assertThat(items).isNotNull();
        assertThat(items.size()).isGreaterThanOrEqualTo(150);

        Set<String> allowedEquipment = Set.of("body only", "dumbbell", "bands", "kettlebells", "exercise ball", "foam roll");
        Set<String> seenIds = new HashSet<>();

        for (ExerciseSeedItem item : items) {
            assertThat(item.getId()).isNotBlank();
            assertThat(seenIds.add(item.getId())).as("Duplicate id: " + item.getId()).isTrue();
            assertThat(item.getImages()).isNotEmpty();
            assertThat(item.getInstructions()).isNotEmpty();
            assertThat(allowedEquipment).contains(item.getEquipment());
        }
    }
}
