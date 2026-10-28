package com.lifetracker.sms;

import java.time.OffsetDateTime;

public interface BankSmsParser {
    String bankShortCode();
    boolean supportsSender(String sender);
    ParseOutcome parse(String text, OffsetDateTime receivedAt);
}
