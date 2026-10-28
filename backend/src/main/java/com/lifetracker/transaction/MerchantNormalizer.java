package com.lifetracker.transaction;

public class MerchantNormalizer {

    public static String normalizeMerchant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String cleaned = raw.toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return cleaned.isEmpty() ? null : cleaned;
    }
}
