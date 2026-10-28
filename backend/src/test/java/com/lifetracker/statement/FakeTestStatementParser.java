package com.lifetracker.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class FakeTestStatementParser implements BankStatementParser {

    private final String shortCode;

    public FakeTestStatementParser() {
        this("SAMPLE");
    }

    public FakeTestStatementParser(String shortCode) {
        this.shortCode = shortCode;
    }

    @Override
    public String bankShortCode() {
        return shortCode;
    }

    @Override
    public ParsedStatement parse(List<ExtractedLine> lines) {
        ParsedStatement stmt = new ParsedStatement();
        for (ExtractedLine line : lines) {
            String text = line.getText();
            if (text != null && text.contains("|")) {
                String[] parts = text.split("\\|", -1);
                if (parts.length >= 5) {
                    ParsedRow row = new ParsedRow();
                    row.date = LocalDate.parse(parts[0].trim());
                    row.description = parts[1].trim();
                    row.amount = new BigDecimal(parts[2].trim());
                    row.direction = parts[3].trim().toLowerCase();
                    row.balanceAfter = new BigDecimal(parts[4].trim());
                    row.confidence = line.getConfidence();
                    stmt.rows.add(row);
                }
            }
        }
        return stmt;
    }
}
