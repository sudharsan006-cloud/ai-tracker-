package com.lifetracker.fitness;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class WorkoutCalorieCalculator {

    /**
     * Calories burned estimate: round_half_up(MET * weightKg * durationMin / 60).
     * Returns null if weightKg is null or MET is null.
     */
    public static Integer calculate(BigDecimal met, BigDecimal weightKg, int durationMin) {
        if (met == null || weightKg == null || durationMin <= 0) {
            return null;
        }

        BigDecimal duration = BigDecimal.valueOf(durationMin);
        BigDecimal calories = met.multiply(weightKg).multiply(duration)
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);

        return calories.intValue();
    }
}
