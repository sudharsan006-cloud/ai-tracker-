package com.lifetracker.ai;

import com.lifetracker.ai.guard.InsightGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InsightGuardTest {

    @Test
    @DisplayName("Test 1: accepts text whose numbers all appear in facts")
    void testAcceptsValidNumbersAndNormalizations() {
        String factsJson = """
            {
              "totalSpent": "12450.00",
              "daysElapsed": 25,
              "averageSteps": 13
            }
            """;

        String text = "You spent ₹12,450.00 over 25 days with an average of 13.0 steps.";
        InsightGuard.GuardResult res = InsightGuard.check(text, factsJson);
        assertThat(res.ok()).isTrue();
        assertThat(res.cleanText()).isEqualTo("You spent ₹12,450.00 over 25 days with an average of 13.0 steps.");
    }

    @Test
    @DisplayName("Test 1: strips * and # and backticks")
    void testStripsFormattingMarks() {
        String factsJson = "{\"steps\": 5000}";
        String text = "## *Summary*: You took `5000` steps today.";
        InsightGuard.GuardResult res = InsightGuard.check(text, factsJson);
        assertThat(res.ok()).isTrue();
        assertThat(res.cleanText()).doesNotContain("*").doesNotContain("#").doesNotContain("`");
        assertThat(res.cleanText()).isEqualTo("Summary: You took 5000 steps today.");
    }

    @Test
    @DisplayName("Test 1: rejects text with one extra number")
    void testRejectsUnallowedNumber() {
        String factsJson = "{\"steps\": 5000}";
        String text = "You took 5000 steps, which is 200 more than yesterday.";
        InsightGuard.GuardResult res = InsightGuard.check(text, factsJson);
        assertThat(res.ok()).isFalse();
        assertThat(res.reason()).isEqualTo("number");
    }

    @Test
    @DisplayName("Test 1: rejects banned phrases")
    void testRejectsBannedPhrases() {
        String factsJson = "{\"spent\": \"100.00\"}";

        String[] banned = new String[] {
                "You should save money.",
                "I recommend trying harder.",
                "This could be a diagnos of stress.",
                "You need to consult a doctor.",
                "Please cut back on dining."
        };

        for (String phrase : banned) {
            InsightGuard.GuardResult res = InsightGuard.check(phrase, factsJson);
            assertThat(res.ok()).isFalse();
            assertThat(res.reason()).isEqualTo("wording");
        }
    }

    @Test
    @DisplayName("Test 1: rejects text over 700 characters")
    void testRejectsOverLength() {
        String factsJson = "{\"val\": 1}";
        String longText = "a".repeat(701);
        InsightGuard.GuardResult res = InsightGuard.check(longText, factsJson);
        assertThat(res.ok()).isFalse();
        assertThat(res.reason()).isEqualTo("length");
    }
}
