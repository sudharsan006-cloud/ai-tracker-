package com.lifetracker.ai.guard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InsightGuard {

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+(?:[.,]\\d+)*");

    private static final List<String> BANNED_PHRASES = List.of(
            "you should",
            "you must",
            "you need to",
            "i recommend",
            "we recommend",
            "recommend",
            "consider ",
            "try to",
            "make sure",
            "advice",
            "advise",
            "diagnos",
            "depress",
            "anxiety",
            "disorder",
            "therapy",
            "medical",
            "doctor",
            "invest",
            "save more",
            "cut back",
            "reduce your",
            "increase your"
    );

    public record GuardResult(boolean ok, String cleanText, String reason) {
        public static GuardResult ok(String cleanText) {
            return new GuardResult(true, cleanText, null);
        }

        public static GuardResult rejected(String reason) {
            return new GuardResult(false, null, reason);
        }
    }

    public static GuardResult check(String text, String factsJson) {
        if (text == null) {
            return GuardResult.rejected("empty");
        }

        // 1. Clean: trim; remove the characters *, # and backticks; collapse repeated blank lines.
        String cleaned = text.trim()
                .replace("*", "")
                .replace("#", "")
                .replace("`", "");

        // Collapse repeated blank lines
        cleaned = cleaned.replaceAll("(\\r?\\n\\s*){2,}", "\n\n").trim();

        if (cleaned.isEmpty()) {
            return GuardResult.rejected("empty");
        }

        // 2. Length: more than 700 characters -> REJECTED
        if (cleaned.length() > 700) {
            return GuardResult.rejected("length");
        }

        // 3. Number guard: build the ALLOWED set
        Set<String> allowed = new HashSet<>();
        allowed.add("0");
        allowed.add("1");

        if (factsJson != null) {
            Matcher factsMatcher = NUMERIC_PATTERN.matcher(factsJson);
            while (factsMatcher.find()) {
                allowed.add(normalizeToken(factsMatcher.group()));
            }
        }

        Matcher textMatcher = NUMERIC_PATTERN.matcher(cleaned);
        while (textMatcher.find()) {
            String token = normalizeToken(textMatcher.group());
            if (!allowed.contains(token)) {
                return GuardResult.rejected("number");
            }
        }

        // 4. Wording guard
        String lower = cleaned.toLowerCase();
        for (String phrase : BANNED_PHRASES) {
            if (lower.contains(phrase)) {
                return GuardResult.rejected("wording");
            }
        }

        return GuardResult.ok(cleaned);
    }

    public static String normalizeToken(String token) {
        if (token == null) {
            return "";
        }
        // remove thousands commas: 1,200.50 -> 1200.50
        String s = token.replace(",", "");
        // remove trailing .0 / .00 and trailing zeros after a decimal point
        if (s.contains(".")) {
            while (s.contains(".") && s.endsWith("0")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }
}
