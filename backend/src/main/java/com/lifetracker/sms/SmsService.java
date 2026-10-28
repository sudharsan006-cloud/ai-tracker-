package com.lifetracker.sms;

import com.lifetracker.common.ConflictException;
import com.lifetracker.common.NotFoundException;
import com.lifetracker.sms.dto.*;
import com.lifetracker.transaction.BankTransaction;
import com.lifetracker.transaction.BankTransactionRepository;
import com.lifetracker.transaction.MerchantNormalizer;
import com.lifetracker.transaction.TransactionMatchingService;
import com.lifetracker.transaction.TransactionService;
import com.lifetracker.transaction.dto.CreateTransactionRequest;
import com.lifetracker.transaction.dto.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final ParserRegistry parserRegistry;
    private final BankTransactionRepository transactionRepository;
    private final UnparsedSmsQueueRepository unparsedSmsQueueRepository;
    private final TransactionMatchingService matchingService;
    private final TransactionService transactionService;
    private final JdbcTemplate jdbcTemplate;

    public SmsService(
            ParserRegistry parserRegistry,
            BankTransactionRepository transactionRepository,
            UnparsedSmsQueueRepository unparsedSmsQueueRepository,
            TransactionMatchingService matchingService,
            TransactionService transactionService,
            JdbcTemplate jdbcTemplate) {
        this.parserRegistry = parserRegistry;
        this.transactionRepository = transactionRepository;
        this.unparsedSmsQueueRepository = unparsedSmsQueueRepository;
        this.matchingService = matchingService;
        this.transactionService = transactionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public static String calculateFingerprint(String sender, long receivedAtMillis, String rawText) {
        String data = (sender != null ? sender : "") + "|" + receivedAtMillis + "|" + (rawText != null ? rawText : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    public SmsIngestResponse ingestMessages(UUID userId, SmsIngestRequest request) {
        if (request.getMessages() == null) {
            return new SmsIngestResponse(0, 0, 0, 0, 0);
        }
        if (request.getMessages().size() > 200) {
            throw new IllegalArgumentException("Maximum 200 messages per request");
        }

        int received = request.getMessages().size();
        int created = 0;
        int queued = 0;
        int ignored = 0;
        int duplicates = 0;

        for (SmsMessageDto msg : request.getMessages()) {
            try {
                Instant receivedAt = msg.getReceivedAt() != null ? msg.getReceivedAt() : Instant.now();
                String rawText = msg.getRawText() != null ? msg.getRawText() : "";
                String sender = msg.getSender() != null ? msg.getSender() : "";

                // 1. Fingerprint
                String fingerprint = calculateFingerprint(sender, receivedAt.toEpochMilli(), rawText);
                if (transactionRepository.existsByUserIdAndSmsFingerprint(userId, fingerprint)
                        || unparsedSmsQueueRepository.existsByUserIdAndSmsFingerprint(userId, fingerprint)) {
                    duplicates++;
                    continue;
                }

                // 2. Parser check
                Optional<BankSmsParser> parserOpt = parserRegistry.findParser(sender);
                if (parserOpt.isEmpty()) {
                    ignored++;
                    continue;
                }

                BankSmsParser parser = parserOpt.get();

                // 3. Parse
                OffsetDateTime receivedAtOffset = receivedAt.atOffset(ZoneOffset.UTC);
                ParseOutcome outcome = parser.parse(rawText, receivedAtOffset);

                if (outcome == null || outcome.status == ParseOutcome.Status.NOT_A_TRANSACTION) {
                    ignored++;
                    continue;
                }

                if (outcome.status == ParseOutcome.Status.FAILED) {
                    UnparsedSmsQueue queueItem = new UnparsedSmsQueue(userId, rawText, sender, receivedAt, fingerprint);
                    unparsedSmsQueueRepository.save(queueItem);
                    queued++;
                    continue;
                }

                // 4. PARSED: Match bank account
                String last4 = outcome.accountLast4;
                if (last4 == null || last4.isBlank()) {
                    UnparsedSmsQueue queueItem = new UnparsedSmsQueue(userId, rawText, sender, receivedAt, fingerprint);
                    unparsedSmsQueueRepository.save(queueItem);
                    queued++;
                    continue;
                }

                String findAccountSql = """
                    SELECT a.id, a.currency_id FROM bank_accounts a
                    JOIN banks b ON b.id = a.bank_id
                    WHERE a.user_id = ? AND a.is_active = true
                      AND b.short_code = ? AND a.account_last4 = ?
                    """;
                List<Map<String, Object>> matchingAccounts = jdbcTemplate.queryForList(
                        findAccountSql, userId, parser.bankShortCode(), last4);

                if (matchingAccounts.size() == 1) {
                    Map<String, Object> accountRow = matchingAccounts.get(0);
                    UUID accountId = (UUID) accountRow.get("id");
                    Integer currencyId = (Integer) accountRow.get("currency_id");

                    BankTransaction tx = new BankTransaction();
                    tx.setUserId(userId);
                    tx.setBankAccountId(accountId);
                    tx.setCurrencyId(currencyId);
                    tx.setAmount(outcome.amount);
                    tx.setDirection(outcome.direction != null ? outcome.direction.toLowerCase() : "debit");
                    tx.setMerchantRaw(outcome.merchantRaw);
                    tx.setMerchantNormalized(MerchantNormalizer.normalizeMerchant(outcome.merchantRaw));
                    tx.setTransactionDate(outcome.transactionDate != null ? outcome.transactionDate : receivedAt);
                    tx.setSource("sms");
                    tx.setSmsFingerprint(fingerprint);
                    tx.setSelfTransfer(false);
                    tx.setReviewStatus("none");

                    tx = transactionRepository.save(tx);
                    matchingService.runMatching(tx);
                    created++;
                } else {
                    UnparsedSmsQueue queueItem = new UnparsedSmsQueue(userId, rawText, sender, receivedAt, fingerprint);
                    unparsedSmsQueueRepository.save(queueItem);
                    queued++;
                }

            } catch (Exception e) {
                // Log warning WITHOUT SMS text
                log.warn("Failed to process SMS message: {}", e.getMessage());
                ignored++;
            }
        }

        return new SmsIngestResponse(received, created, queued, ignored, duplicates);
    }

    @Transactional
    public TransactionResponse resolveUnparsedSms(UUID userId, UUID id, ResolveUnparsedSmsRequest request) {
        UnparsedSmsQueue item = unparsedSmsQueueRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Unparsed SMS queue item not found"));

        if (!"pending".equals(item.getStatus())) {
            throw new ConflictException("Queue item is not pending");
        }

        CreateTransactionRequest txReq = new CreateTransactionRequest();
        txReq.setBankAccountId(request.getBankAccountId());
        txReq.setAmount(request.getAmount());
        txReq.setDirection(request.getDirection());
        txReq.setTransactionDate(request.getTransactionDate() != null ? request.getTransactionDate() : item.getReceivedAt());
        txReq.setMerchant(request.getMerchant());
        txReq.setCategoryId(request.getCategoryId());

        TransactionResponse txResp = transactionService.createTransaction(userId, txReq, "sms", item.getSmsFingerprint());

        item.setStatus("resolved");
        item.setResolvedTransactionId(txResp.getId());
        unparsedSmsQueueRepository.save(item);

        return txResp;
    }

    @Transactional
    public void discardUnparsedSms(UUID userId, UUID id) {
        UnparsedSmsQueue item = unparsedSmsQueueRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Unparsed SMS queue item not found"));

        item.setStatus("discarded");
        unparsedSmsQueueRepository.save(item);
    }
}
