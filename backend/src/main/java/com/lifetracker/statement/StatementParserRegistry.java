package com.lifetracker.statement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class StatementParserRegistry {

    private final List<BankStatementParser> parsers;

    public StatementParserRegistry(@Autowired(required = false) List<BankStatementParser> parsers) {
        this.parsers = parsers != null ? parsers : Collections.emptyList();
    }

    public Optional<BankStatementParser> find(String bankShortCode) {
        if (bankShortCode == null || bankShortCode.isBlank()) {
            return Optional.empty();
        }
        return parsers.stream()
                .filter(p -> bankShortCode.equalsIgnoreCase(p.bankShortCode()))
                .findFirst();
    }
}
