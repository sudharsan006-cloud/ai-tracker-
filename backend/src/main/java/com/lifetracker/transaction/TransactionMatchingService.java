package com.lifetracker.transaction;

import com.lifetracker.config.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@Service
public class TransactionMatchingService {

    private static final Logger log = LoggerFactory.getLogger(TransactionMatchingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ConfigService configService;
    private final BankTransactionRepository transactionRepository;

    public TransactionMatchingService(
            JdbcTemplate jdbcTemplate,
            ConfigService configService,
            BankTransactionRepository transactionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.configService = configService;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void runMatching(BankTransaction newTx) {
        BigDecimal dedupHours = configService.get("dedup_match_window_hours");
        BigDecimal selfTransferHours = configService.get("self_transfer_match_window_hours");
        BigDecimal refundThreshold = configService.get("refund_match_similarity_threshold");
        BigDecimal refundDays = configService.get("refund_match_window_days");

        // Check 1: possible duplicate
        String dupSql = """
            SELECT id FROM bank_transactions
            WHERE user_id = ?
              AND bank_account_id = ?
              AND amount = ?
              AND direction = ?
              AND id <> ?
              AND review_status = 'none'
              AND abs(extract(epoch from (transaction_date - ?))) <= ? * 3600
            ORDER BY abs(extract(epoch from (transaction_date - ?))) ASC
            LIMIT 1
            """;
        try {
            Timestamp newTs = Timestamp.from(newTx.getTransactionDate());
            UUID partnerId = jdbcTemplate.queryForObject(
                    dupSql,
                    UUID.class,
                    newTx.getUserId(),
                    newTx.getBankAccountId(),
                    newTx.getAmount(),
                    newTx.getDirection(),
                    newTx.getId(),
                    newTs,
                    dedupHours.doubleValue(),
                    newTs
            );
            if (partnerId != null) {
                newTx.setReviewStatus("possible_duplicate");
                newTx.setReviewPartnerId(partnerId);
                transactionRepository.save(newTx);

                transactionRepository.findById(partnerId).ifPresent(partner -> {
                    partner.setReviewStatus("possible_duplicate");
                    partner.setReviewPartnerId(newTx.getId());
                    transactionRepository.save(partner);
                });
                return;
            }
        } catch (EmptyResultDataAccessException e) {
            // Not a duplicate
        }

        // Check 2: possible self-transfer
        String oppositeDir = "debit".equalsIgnoreCase(newTx.getDirection()) ? "credit" : "debit";
        String selfSql = """
            SELECT id FROM bank_transactions
            WHERE user_id = ?
              AND bank_account_id <> ?
              AND currency_id = ?
              AND amount = ?
              AND direction = ?
              AND is_self_transfer = false
              AND review_status = 'none'
              AND abs(extract(epoch from (transaction_date - ?))) <= ? * 3600
            ORDER BY abs(extract(epoch from (transaction_date - ?))) ASC
            LIMIT 1
            """;
        try {
            Timestamp newTs = Timestamp.from(newTx.getTransactionDate());
            UUID partnerId = jdbcTemplate.queryForObject(
                    selfSql,
                    UUID.class,
                    newTx.getUserId(),
                    newTx.getBankAccountId(),
                    newTx.getCurrencyId(),
                    newTx.getAmount(),
                    oppositeDir,
                    newTs,
                    selfTransferHours.doubleValue(),
                    newTs
            );
            if (partnerId != null) {
                newTx.setReviewStatus("possible_self_transfer");
                newTx.setReviewPartnerId(partnerId);
                transactionRepository.save(newTx);

                transactionRepository.findById(partnerId).ifPresent(partner -> {
                    partner.setReviewStatus("possible_self_transfer");
                    partner.setReviewPartnerId(newTx.getId());
                    transactionRepository.save(partner);
                });
                return;
            }
        } catch (EmptyResultDataAccessException e) {
            // Not a self-transfer
        }

        // Check 3: possible refund (only if new row is a credit and has merchantNormalized)
        if ("credit".equalsIgnoreCase(newTx.getDirection()) && newTx.getMerchantNormalized() != null) {
            String refundSql = """
                SELECT id FROM bank_transactions
                WHERE user_id = ?
                  AND currency_id = ?
                  AND direction = 'debit'
                  AND refund_matched_id IS NULL
                  AND is_self_transfer = false
                  AND amount >= ?
                  AND transaction_date <= ?
                  AND extract(epoch from (? - transaction_date)) <= ? * 86400
                  AND merchant_normalized IS NOT NULL
                  AND similarity(merchant_normalized, ?) >= ?
                ORDER BY similarity(merchant_normalized, ?) DESC
                LIMIT 1
                """;
            try {
                Timestamp newTs = Timestamp.from(newTx.getTransactionDate());
                UUID debitId = jdbcTemplate.queryForObject(
                        refundSql,
                        UUID.class,
                        newTx.getUserId(),
                        newTx.getCurrencyId(),
                        newTx.getAmount(),
                        newTs,
                        newTs,
                        refundDays.doubleValue(),
                        newTx.getMerchantNormalized(),
                        refundThreshold.doubleValue(),
                        newTx.getMerchantNormalized()
                );
                if (debitId != null) {
                    newTx.setReviewStatus("possible_refund_match");
                    newTx.setReviewPartnerId(debitId);
                    transactionRepository.save(newTx);
                    // Debit row is NOT flagged
                    return;
                }
            } catch (EmptyResultDataAccessException e) {
                // Not a refund
            }
        }
    }
}
