package com.lifetracker.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ParsedRow {
    public LocalDate date;          // null if it could not be read
    public String description;      // may be null
    public BigDecimal amount;       // positive; null if it could not be read
    public String direction;        // "debit" | "credit" | null
    public BigDecimal balanceAfter; // may be null
    public double confidence;       // lowest confidence among the lines used by this row
    public boolean parseError;      // true if the line looked like a row but could not be fully read
}
