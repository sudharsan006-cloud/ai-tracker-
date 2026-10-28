package com.lifetracker.sms;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class ParserRegistry {

    private final List<BankSmsParser> parsers;

    public ParserRegistry(List<BankSmsParser> parsers) {
        this.parsers = parsers != null ? parsers : Collections.emptyList();
    }

    public Optional<BankSmsParser> findParser(String sender) {
        if (sender == null) {
            return Optional.empty();
        }
        return parsers.stream()
                .filter(p -> p.supportsSender(sender))
                .findFirst();
    }

    public List<BankSmsParser> getParsers() {
        return parsers;
    }
}
