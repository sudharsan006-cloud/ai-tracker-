package com.lifetracker.fitness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

public class BmrCalculator {

    /**
     * bmrEstimate(sexAtBirth, weightKg, heightCm, dateOfBirth, onDate) -> Integer or null.
     * - Return null if any input is null, or sexAtBirth is not male/female.
     * - age = whole years between dateOfBirth and onDate.
     * - base = 10 * weightKg + 6.25 * heightCm - 5 * age (use BigDecimal).
     * - male: base + 5; female: base - 161.
     * - Round HALF_UP to an integer.
     */
    public static Integer calculate(String sexAtBirth, BigDecimal weightKg, BigDecimal heightCm,
                                    LocalDate dateOfBirth, LocalDate onDate) {
        if (sexAtBirth == null || weightKg == null || heightCm == null || dateOfBirth == null || onDate == null) {
            return null;
        }

        String sex = sexAtBirth.trim().toLowerCase();
        if (!"male".equals(sex) && !"female".equals(sex)) {
            return null;
        }

        if (onDate.isBefore(dateOfBirth)) {
            return null;
        }

        int age = Period.between(dateOfBirth, onDate).getYears();
        if (age < 0) {
            return null;
        }

        // base = 10 * weightKg + 6.25 * heightCm - 5 * age
        BigDecimal term1 = BigDecimal.valueOf(10).multiply(weightKg);
        BigDecimal term2 = new BigDecimal("6.25").multiply(heightCm);
        BigDecimal term3 = BigDecimal.valueOf(5).multiply(BigDecimal.valueOf(age));

        BigDecimal base = term1.add(term2).subtract(term3);

        BigDecimal bmr;
        if ("male".equals(sex)) {
            bmr = base.add(BigDecimal.valueOf(5));
        } else {
            bmr = base.subtract(BigDecimal.valueOf(161));
        }

        return bmr.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
