package com.lifetracker.statement;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BalanceChecker {

    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    public static class BalanceCheckResult {
        private final Set<Integer> mismatchRowIndexes;
        private final boolean closingMismatch;
        private final String gateStatus;

        public BalanceCheckResult(Set<Integer> mismatchRowIndexes, boolean closingMismatch, String gateStatus) {
            this.mismatchRowIndexes = mismatchRowIndexes;
            this.closingMismatch = closingMismatch;
            this.gateStatus = gateStatus;
        }

        public Set<Integer> getMismatchRowIndexes() {
            return mismatchRowIndexes;
        }

        public boolean isClosingMismatch() {
            return closingMismatch;
        }

        public String getGateStatus() {
            return gateStatus;
        }
    }

    public static BalanceCheckResult check(BigDecimal openingBalance, BigDecimal closingBalance, List<ParsedRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return new BalanceCheckResult(Collections.emptySet(), false, "not_checkable");
        }

        Set<Integer> mismatchIndexes = new HashSet<>();
        BigDecimal prev = openingBalance;
        int checkedRowsCount = 0;

        for (int i = 0; i < rows.size(); i++) {
            ParsedRow row = rows.get(i);
            if (row.balanceAfter != null) {
                if (prev != null && row.amount != null && row.direction != null) {
                    BigDecimal delta = "credit".equalsIgnoreCase(row.direction) ? row.amount : row.amount.negate();
                    BigDecimal expected = prev.add(delta);
                    checkedRowsCount++;
                    if (expected.subtract(row.balanceAfter).abs().compareTo(EPSILON) > 0) {
                        mismatchIndexes.add(i);
                    }
                }
                prev = row.balanceAfter;
            } else {
                if (prev != null && row.amount != null && row.direction != null) {
                    BigDecimal delta = "credit".equalsIgnoreCase(row.direction) ? row.amount : row.amount.negate();
                    prev = prev.add(delta);
                } else {
                    prev = null;
                }
            }
        }

        // Closing check
        boolean closingCheckRan = false;
        boolean closingMismatch = false;

        boolean allRowsHaveAmountAndDirection = rows.stream()
                .allMatch(r -> r.amount != null && r.direction != null);

        if (openingBalance != null && closingBalance != null && allRowsHaveAmountAndDirection) {
            closingCheckRan = true;
            BigDecimal expected = openingBalance;
            for (ParsedRow row : rows) {
                BigDecimal delta = "credit".equalsIgnoreCase(row.direction) ? row.amount : row.amount.negate();
                expected = expected.add(delta);
            }
            if (expected.subtract(closingBalance).abs().compareTo(EPSILON) > 0) {
                closingMismatch = true;
            }
        }

        String gateStatus;
        if (!mismatchIndexes.isEmpty() || closingMismatch) {
            gateStatus = "failed";
        } else if (closingCheckRan || checkedRowsCount > 0) {
            gateStatus = "passed";
        } else {
            gateStatus = "not_checkable";
        }

        return new BalanceCheckResult(mismatchIndexes, closingMismatch, gateStatus);
    }
}
