package com.lifetracker.statement;

import java.util.List;

public interface BankStatementParser {
    String bankShortCode();
    ParsedStatement parse(List<ExtractedLine> lines);
}
