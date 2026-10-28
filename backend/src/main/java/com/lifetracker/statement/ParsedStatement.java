package com.lifetracker.statement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ParsedStatement {
    public BigDecimal openingBalance;   // may be null
    public BigDecimal closingBalance;   // may be null
    public List<ParsedRow> rows = new ArrayList<>();
}
