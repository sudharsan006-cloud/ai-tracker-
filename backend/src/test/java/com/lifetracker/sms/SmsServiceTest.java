package com.lifetracker.sms;

import com.lifetracker.sms.dto.ResolveUnparsedSmsRequest;
import com.lifetracker.sms.dto.SmsIngestRequest;
import com.lifetracker.sms.dto.SmsIngestResponse;
import com.lifetracker.sms.dto.SmsMessageDto;
import com.lifetracker.transaction.BankTransaction;
import com.lifetracker.transaction.BankTransactionRepository;
import com.lifetracker.transaction.TransactionMatchingService;
import com.lifetracker.transaction.TransactionService;
import com.lifetracker.transaction.dto.CreateTransactionRequest;
import com.lifetracker.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class SmsServiceTest {

    private BankTransactionRepository transactionRepository;
    private UnparsedSmsQueueRepository unparsedSmsQueueRepository;
    private TransactionMatchingService matchingService;
    private TransactionService transactionService;
    private JdbcTemplate jdbcTemplate;

    private static class FakeTestParser implements BankSmsParser {
        @Override
        public String bankShortCode() {
            return "SAMPLE";
        }

        @Override
        public boolean supportsSender(String sender) {
            return sender != null && sender.toUpperCase().contains("SAMPLEBK");
        }

        @Override
        public ParseOutcome parse(String text, OffsetDateTime receivedAt) {
            if (text.contains("OTP") || text.contains("promo")) {
                return ParseOutcome.notATransaction();
            }
            if (text.contains("unparseable")) {
                return ParseOutcome.failed();
            }
            if (text.contains("debited")) {
                String last4 = "1234";
                if (text.contains("two_accounts")) {
                    last4 = "9999";
                }
                return ParseOutcome.parsed(
                        new BigDecimal("150.00"),
                        "debit",
                        "Cafe Coffee Day",
                        last4,
                        receivedAt.toInstant()
                );
            }
            return ParseOutcome.failed();
        }
    }

    private SmsService smsService;

    @BeforeEach
    void setUp() {
        transactionRepository = Mockito.mock(BankTransactionRepository.class);
        unparsedSmsQueueRepository = Mockito.mock(UnparsedSmsQueueRepository.class);
        matchingService = Mockito.mock(TransactionMatchingService.class);
        transactionService = Mockito.mock(TransactionService.class);
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        ParserRegistry registry = new ParserRegistry(List.of(new FakeTestParser()));
        smsService = new SmsService(
                registry,
                transactionRepository,
                unparsedSmsQueueRepository,
                matchingService,
                transactionService,
                jdbcTemplate
        );
    }

    @Test
    @DisplayName("Test 12: SMS ingest test scenarios (created, duplicates, ignored, queued)")
    void testSmsIngestScenarios() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        // Account with last4 1234
        when(jdbcTemplate.queryForList(anyString(), eq(userId), eq("SAMPLE"), eq("1234")))
                .thenReturn(List.of(Map.of("id", accountId, "currency_id", 1)));

        // Account with last4 9999 matches two accounts
        when(jdbcTemplate.queryForList(anyString(), eq(userId), eq("SAMPLE"), eq("9999")))
                .thenReturn(List.of(
                        Map.of("id", UUID.randomUUID(), "currency_id", 1),
                        Map.of("id", UUID.randomUUID(), "currency_id", 1)
                ));

        when(transactionRepository.save(any(BankTransaction.class))).thenAnswer(i -> {
            BankTransaction tx = i.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        // 1. Known sender with transaction text matching 1 account -> created
        SmsMessageDto msg1 = new SmsMessageDto("VM-SAMPLEBK", "debited Rs 150 from ac 1234 at CCD", Instant.now());
        SmsIngestResponse resp1 = smsService.ingestMessages(userId, new SmsIngestRequest(List.of(msg1)));
        assertThat(resp1.getCreated()).isEqualTo(1);
        assertThat(resp1.getDuplicates()).isEqualTo(0);
        assertThat(resp1.getIgnored()).isEqualTo(0);
        assertThat(resp1.getQueued()).isEqualTo(0);

        // 2. Same message sent again -> duplicate
        String fp = SmsService.calculateFingerprint("VM-SAMPLEBK", msg1.getReceivedAt().toEpochMilli(), msg1.getRawText());
        when(transactionRepository.existsByUserIdAndSmsFingerprint(userId, fp)).thenReturn(true);
        SmsIngestResponse resp2 = smsService.ingestMessages(userId, new SmsIngestRequest(List.of(msg1)));
        assertThat(resp2.getDuplicates()).isEqualTo(1);

        // 3. Unknown sender -> ignored
        SmsMessageDto unknownMsg = new SmsMessageDto("VM-UNKNOWN", "debited Rs 150", Instant.now());
        SmsIngestResponse resp3 = smsService.ingestMessages(userId, new SmsIngestRequest(List.of(unknownMsg)));
        assertThat(resp3.getIgnored()).isEqualTo(1);

        // 4. Known sender OTP text -> ignored
        SmsMessageDto otpMsg = new SmsMessageDto("VM-SAMPLEBK", "Your OTP is 123456", Instant.now());
        SmsIngestResponse resp4 = smsService.ingestMessages(userId, new SmsIngestRequest(List.of(otpMsg)));
        assertThat(resp4.getIgnored()).isEqualTo(1);

        // 5. Transaction-like text that cannot be parsed -> queued
        SmsMessageDto failMsg = new SmsMessageDto("VM-SAMPLEBK", "unparseable text", Instant.now());
        SmsIngestResponse resp5 = smsService.ingestMessages(userId, new SmsIngestRequest(List.of(failMsg)));
        assertThat(resp5.getQueued()).isEqualTo(1);

        // 6. Text whose last4 matches two accounts -> queued
        SmsMessageDto twoAccountsMsg = new SmsMessageDto("VM-SAMPLEBK", "debited two_accounts", Instant.now());
        SmsIngestResponse resp6 = smsService.ingestMessages(userId, new SmsIngestRequest(List.of(twoAccountsMsg)));
        assertThat(resp6.getQueued()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 13: Resolve a queued SMS creates a transaction and marks it resolved; discard marks it discarded")
    void testResolveAndDiscardQueuedSms() {
        UUID userId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        UnparsedSmsQueue queueItem = new UnparsedSmsQueue(userId, "raw text", "VM-SAMPLEBK", Instant.now(), "fp123");
        queueItem.setId(queueId);
        queueItem.setStatus("pending");

        when(unparsedSmsQueueRepository.findByIdAndUserId(queueId, userId)).thenReturn(Optional.of(queueItem));

        TransactionResponse txResp = new TransactionResponse();
        txResp.setId(txId);
        when(transactionService.createTransaction(eq(userId), any(CreateTransactionRequest.class), eq("sms"), eq("fp123")))
                .thenReturn(txResp);

        ResolveUnparsedSmsRequest resolveReq = new ResolveUnparsedSmsRequest();
        resolveReq.setBankAccountId(accountId);
        resolveReq.setAmount(new BigDecimal("250.00"));
        resolveReq.setDirection("debit");

        TransactionResponse resolved = smsService.resolveUnparsedSms(userId, queueId, resolveReq);
        assertThat(resolved.getId()).isEqualTo(txId);
        assertThat(queueItem.getStatus()).isEqualTo("resolved");
        assertThat(queueItem.getResolvedTransactionId()).isEqualTo(txId);

        // Discard
        UUID discardId = UUID.randomUUID();
        UnparsedSmsQueue itemToDiscard = new UnparsedSmsQueue(userId, "raw", "VM-SAMPLEBK", Instant.now(), "fp456");
        itemToDiscard.setId(discardId);
        when(unparsedSmsQueueRepository.findByIdAndUserId(discardId, userId)).thenReturn(Optional.of(itemToDiscard));

        smsService.discardUnparsedSms(userId, discardId);
        assertThat(itemToDiscard.getStatus()).isEqualTo("discarded");
    }
}
