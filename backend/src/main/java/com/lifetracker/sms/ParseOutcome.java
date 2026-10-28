package com.lifetracker.sms;

import java.math.BigDecimal;
import java.time.Instant;

public class ParseOutcome {

    public enum Status {
        PARSED,
        NOT_A_TRANSACTION,
        FAILED
    }

    public final Status status;
    public final BigDecimal amount;
    public final String direction;
    public final String merchantRaw;
    public final String accountLast4;
    public final Instant transactionDate;

    private ParseOutcome(
            Status status,
            BigDecimal amount,
            String direction,
            String merchantRaw,
            String accountLast4,
            Instant transactionDate) {
        this.status = status;
        this.amount = amount;
        this.direction = direction;
        this.merchantRaw = merchantRaw;
        this.accountLast4 = accountLast4;
        this.transactionDate = transactionDate;
    }

    public static ParseOutcome parsed(
            BigDecimal amount,
            String direction,
            String merchantRaw,
            String accountLast4,
            Instant transactionDate) {
        return new ParseOutcome(Status.PARSED, amount, direction, merchantRaw, accountLast4, transactionDate);
    }

    public static ParseOutcome notATransaction() {
        return new ParseOutcome(Status.NOT_A_TRANSACTION, null, null, null, null, null);
    }

    public static ParseOutcome failed() {
        return new ParseOutcome(Status.FAILED, null, null, null, null, null);
    }
}
