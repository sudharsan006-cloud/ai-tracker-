package com.lifetracker.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BalanceCheckerTest {

    private ParsedRow createRow(String date, String desc, String amount, String direction, String balanceAfter) {
        ParsedRow r = new ParsedRow();
        r.date = LocalDate.parse(date);
        r.description = desc;
        r.amount = new BigDecimal(amount);
        r.direction = direction;
        r.balanceAfter = balanceAfter != null ? new BigDecimal(balanceAfter) : null;
        r.confidence = 100.0;
        return r;
    }

    @Test
    @DisplayName("Test 1: All consistent -> no flags and gate_status passed")
    void testAllConsistent() {
        BigDecimal opening = new BigDecimal("1000.00");
        BigDecimal closing = new BigDecimal("1250.00");

        List<ParsedRow> rows = new ArrayList<>();
        // 1000 - 250 = 750
        rows.add(createRow("2026-10-01", "Coffee", "250.00", "debit", "750.00"));
        // 750 + 500 = 1250
        rows.add(createRow("2026-10-02", "Salary bonus", "500.00", "credit", "1250.00"));

        BalanceChecker.BalanceCheckResult result = BalanceChecker.check(opening, closing, rows);

        assertThat(result.getMismatchRowIndexes()).isEmpty();
        assertThat(result.isClosingMismatch()).isFalse();
        assertThat(result.getGateStatus()).isEqualTo("passed");
    }

    @Test
    @DisplayName("Test 2: One wrong amount -> ONLY that row flagged balance_mismatch and next row resyncs")
    void testOneWrongAmountResyncs() {
        BigDecimal opening = new BigDecimal("1000.00");
        BigDecimal closing = new BigDecimal("1250.00");

        List<ParsedRow> rows = new ArrayList<>();
        // expected: 1000 - 250 = 750, but printed is 800.00 -> mismatch on row 0
        rows.add(createRow("2026-10-01", "Coffee", "250.00", "debit", "800.00"));
        // row 1: prev trusted as 800.00. 800 + 450 = 1250 -> row 1 matches!
        rows.add(createRow("2026-10-02", "Refund", "450.00", "credit", "1250.00"));

        BalanceChecker.BalanceCheckResult result = BalanceChecker.check(opening, closing, rows);

        assertThat(result.getMismatchRowIndexes()).containsExactly(0);
        assertThat(result.isClosingMismatch()).isFalse();
        assertThat(result.getGateStatus()).isEqualTo("failed");
    }

    @Test
    @DisplayName("Test 3: Closing balance mismatch with no row mismatch -> failed")
    void testClosingBalanceMismatch() {
        BigDecimal opening = new BigDecimal("1000.00");
        BigDecimal closing = new BigDecimal("9999.00"); // mismatching closing

        List<ParsedRow> rows = new ArrayList<>();
        // no balanceAfter on row
        rows.add(createRow("2026-10-01", "Coffee", "250.00", "debit", null));

        BalanceChecker.BalanceCheckResult result = BalanceChecker.check(opening, closing, rows);

        assertThat(result.getMismatchRowIndexes()).isEmpty();
        assertThat(result.isClosingMismatch()).isTrue();
        assertThat(result.getGateStatus()).isEqualTo("failed");
    }

    @Test
    @DisplayName("Test 4: No balances at all -> not_checkable")
    void testNoBalancesAtAll() {
        List<ParsedRow> rows = new ArrayList<>();
        rows.add(createRow("2026-10-01", "Coffee", "250.00", "debit", null));
        rows.add(createRow("2026-10-02", "Lunch", "300.00", "debit", null));

        BalanceChecker.BalanceCheckResult result = BalanceChecker.check(null, null, rows);

        assertThat(result.getMismatchRowIndexes()).isEmpty();
        assertThat(result.isClosingMismatch()).isFalse();
        assertThat(result.getGateStatus()).isEqualTo("not_checkable");
    }
}
