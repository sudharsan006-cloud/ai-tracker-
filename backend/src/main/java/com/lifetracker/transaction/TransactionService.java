package com.lifetracker.transaction;

import com.lifetracker.bank.BankAccount;
import com.lifetracker.bank.BankAccountRepository;
import com.lifetracker.category.Category;
import com.lifetracker.category.CategoryRepository;
import com.lifetracker.common.ConflictException;
import com.lifetracker.common.NotFoundException;
import com.lifetracker.sms.UnparsedSmsQueue;
import com.lifetracker.sms.UnparsedSmsQueueRepository;
import com.lifetracker.transaction.dto.*;
import com.lifetracker.user.UserProfile;
import com.lifetracker.user.UserProfileRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class TransactionService {

    private final BankTransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final CategoryRepository categoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final UnparsedSmsQueueRepository unparsedSmsQueueRepository;
    private final TransactionMatchingService matchingService;
    private final JdbcTemplate jdbcTemplate;

    public TransactionService(
            BankTransactionRepository transactionRepository,
            BankAccountRepository bankAccountRepository,
            CategoryRepository categoryRepository,
            UserProfileRepository userProfileRepository,
            UnparsedSmsQueueRepository unparsedSmsQueueRepository,
            TransactionMatchingService matchingService,
            JdbcTemplate jdbcTemplate) {
        this.transactionRepository = transactionRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.categoryRepository = categoryRepository;
        this.userProfileRepository = userProfileRepository;
        this.unparsedSmsQueueRepository = unparsedSmsQueueRepository;
        this.matchingService = matchingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    private ZoneId getUserZoneId(UUID userId) {
        return userProfileRepository.findById(userId)
                .map(UserProfile::getTimezone)
                .map(tz -> {
                    try {
                        return ZoneId.of(tz);
                    } catch (Exception e) {
                        return ZoneId.of("Asia/Kolkata");
                    }
                })
                .orElse(ZoneId.of("Asia/Kolkata"));
    }

    @Transactional
    public TransactionResponse createTransaction(UUID userId, CreateTransactionRequest request, String source, String smsFingerprint) {
        // Validate Bank Account
        BankAccount account = bankAccountRepository.findByIdAndUserId(request.getBankAccountId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found or does not belong to user"));
        if (!account.isActive()) {
            throw new IllegalArgumentException("Bank account is inactive");
        }

        // Validate Amount
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (request.getAmount().stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount can have at most 2 decimal places");
        }

        // Validate Direction
        if (!"debit".equalsIgnoreCase(request.getDirection()) && !"credit".equalsIgnoreCase(request.getDirection())) {
            throw new IllegalArgumentException("Direction must be 'debit' or 'credit'");
        }

        // Validate Category
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            if (!category.isActive() || (category.getUserId() != null && !category.getUserId().equals(userId))) {
                throw new IllegalArgumentException("Invalid category for user");
            }
        }

        // Validate Date
        Instant txDate = request.getTransactionDate() != null ? request.getTransactionDate() : Instant.now();
        if (txDate.isAfter(Instant.now().plus(Duration.ofDays(1)))) {
            throw new IllegalArgumentException("Transaction date cannot be more than 1 day in the future");
        }

        BankTransaction tx = new BankTransaction();
        tx.setUserId(userId);
        tx.setBankAccountId(account.getId());
        tx.setCurrencyId(account.getCurrencyId());
        tx.setCategoryId(request.getCategoryId());
        tx.setAmount(request.getAmount());
        tx.setDirection(request.getDirection().toLowerCase());
        tx.setMerchantRaw(request.getMerchant());
        tx.setMerchantNormalized(MerchantNormalizer.normalizeMerchant(request.getMerchant()));
        tx.setTransactionDate(txDate);
        tx.setSource(source != null ? source : "manual");
        tx.setSmsFingerprint(smsFingerprint);
        tx.setSelfTransfer(false);
        tx.setReviewStatus("none");

        tx = transactionRepository.save(tx);

        matchingService.runMatching(tx);

        return TransactionResponse.fromEntity(tx);
    }

    public TransactionPageResponse getTransactions(
            UUID userId,
            String from,
            String to,
            UUID categoryId,
            UUID bankAccountId,
            String direction,
            int page,
            int size) {

        if (size <= 0) size = 50;
        if (size > 200) size = 200;
        if (page < 0) page = 0;

        ZoneId zoneId = getUserZoneId(userId);

        StringBuilder whereSql = new StringBuilder(" WHERE user_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (from != null && !from.isBlank()) {
            LocalDate fromDate = LocalDate.parse(from);
            Instant fromInstant = fromDate.atStartOfDay(zoneId).toInstant();
            whereSql.append(" AND transaction_date >= ? ");
            params.add(Timestamp.from(fromInstant));
        }

        if (to != null && !to.isBlank()) {
            LocalDate toDate = LocalDate.parse(to);
            Instant toInstant = toDate.plusDays(1).atStartOfDay(zoneId).toInstant();
            whereSql.append(" AND transaction_date < ? ");
            params.add(Timestamp.from(toInstant));
        }

        if (categoryId != null) {
            whereSql.append(" AND category_id = ? ");
            params.add(categoryId);
        }

        if (bankAccountId != null) {
            whereSql.append(" AND bank_account_id = ? ");
            params.add(bankAccountId);
        }

        if (direction != null && !direction.isBlank()) {
            whereSql.append(" AND direction = ? ");
            params.add(direction.toLowerCase());
        }

        String countSql = "SELECT COUNT(*) FROM bank_transactions " + whereSql;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long totalCount = total != null ? total : 0;

        String selectSql = "SELECT * FROM bank_transactions " + whereSql
                + " ORDER BY transaction_date DESC LIMIT ? OFFSET ?";
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(size);
        queryParams.add(page * size);

        List<TransactionResponse> items = jdbcTemplate.query(selectSql, (rs, rowNum) -> {
            TransactionResponse resp = new TransactionResponse();
            resp.setId((UUID) rs.getObject("id"));
            resp.setBankAccountId((UUID) rs.getObject("bank_account_id"));
            resp.setCurrencyId(rs.getInt("currency_id"));
            resp.setCategoryId((UUID) rs.getObject("category_id"));
            resp.setAmount(rs.getBigDecimal("amount"));
            resp.setDirection(rs.getString("direction"));
            resp.setMerchantRaw(rs.getString("merchant_raw"));
            Timestamp ts = rs.getTimestamp("transaction_date");
            resp.setTransactionDate(ts != null ? ts.toInstant() : null);
            resp.setSource(rs.getString("source"));
            resp.setSelfTransfer(rs.getBoolean("is_self_transfer"));
            resp.setReviewStatus(rs.getString("review_status"));
            resp.setReviewPartnerId((UUID) rs.getObject("review_partner_id"));
            resp.setSelfTransferMatchedId((UUID) rs.getObject("self_transfer_matched_id"));
            resp.setRefundMatchedId((UUID) rs.getObject("refund_matched_id"));
            return resp;
        }, queryParams.toArray());

        return new TransactionPageResponse(items, page, size, totalCount);
    }

    @Transactional
    public TransactionResponse patchTransaction(UUID userId, UUID id, PatchTransactionRequest request) {
        BankTransaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        boolean isLinked = tx.isSelfTransfer() || tx.getRefundMatchedId() != null;
        boolean hasReviewFlag = tx.getReviewStatus() != null && !"none".equals(tx.getReviewStatus());

        if (isLinked || hasReviewFlag) {
            if (request.getAmount() != null || request.getDirection() != null
                    || request.getTransactionDate() != null || request.getBankAccountId() != null) {
                throw new ConflictException("Unlink or resolve the match first");
            }
        }

        if (request.getBankAccountId() != null) {
            BankAccount account = bankAccountRepository.findByIdAndUserId(request.getBankAccountId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Bank account not found or does not belong to user"));
            if (!account.isActive()) {
                throw new IllegalArgumentException("Bank account is inactive");
            }
            tx.setBankAccountId(account.getId());
            tx.setCurrencyId(account.getCurrencyId());
        }

        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be greater than 0");
            }
            if (request.getAmount().stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("Amount can have at most 2 decimal places");
            }
            tx.setAmount(request.getAmount());
        }

        if (request.getDirection() != null) {
            if (!"debit".equalsIgnoreCase(request.getDirection()) && !"credit".equalsIgnoreCase(request.getDirection())) {
                throw new IllegalArgumentException("Direction must be 'debit' or 'credit'");
            }
            tx.setDirection(request.getDirection().toLowerCase());
        }

        if (request.getTransactionDate() != null) {
            if (request.getTransactionDate().isAfter(Instant.now().plus(Duration.ofDays(1)))) {
                throw new IllegalArgumentException("Transaction date cannot be more than 1 day in the future");
            }
            tx.setTransactionDate(request.getTransactionDate());
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            if (!category.isActive() || (category.getUserId() != null && !category.getUserId().equals(userId))) {
                throw new IllegalArgumentException("Invalid category for user");
            }
            tx.setCategoryId(request.getCategoryId());
        }

        if (request.getMerchant() != null) {
            tx.setMerchantRaw(request.getMerchant());
            tx.setMerchantNormalized(MerchantNormalizer.normalizeMerchant(request.getMerchant()));
        }

        tx = transactionRepository.save(tx);
        return TransactionResponse.fromEntity(tx);
    }

    @Transactional
    public void deleteTransaction(UUID userId, UUID id) {
        BankTransaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        // Step 1: If is_self_transfer and self_transfer_matched_id is set
        if (tx.isSelfTransfer() && tx.getSelfTransferMatchedId() != null) {
            transactionRepository.findById(tx.getSelfTransferMatchedId()).ifPresent(partner -> {
                partner.setSelfTransfer(false);
                partner.setSelfTransferMatchedId(null);
                transactionRepository.save(partner);
            });
        }

        // Step 2: Set review_status='none', review_partner_id=null on every row whose review_partner_id = this id
        transactionRepository.clearReviewFlagsForPartner(id);

        // Step 3: Delete the row
        transactionRepository.delete(tx);
    }

    @Transactional
    public void unlinkTransaction(UUID userId, UUID id) {
        BankTransaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        if (tx.isSelfTransfer()) {
            UUID partnerId = tx.getSelfTransferMatchedId();
            tx.setSelfTransfer(false);
            tx.setSelfTransferMatchedId(null);
            transactionRepository.save(tx);

            if (partnerId != null) {
                transactionRepository.findById(partnerId).ifPresent(partner -> {
                    partner.setSelfTransfer(false);
                    partner.setSelfTransferMatchedId(null);
                    transactionRepository.save(partner);
                });
            }
        } else if (tx.getRefundMatchedId() != null) {
            UUID partnerId = tx.getRefundMatchedId();
            tx.setRefundMatchedId(null);
            transactionRepository.save(tx);

            if (partnerId != null) {
                transactionRepository.findById(partnerId).ifPresent(partner -> {
                    partner.setRefundMatchedId(null);
                    transactionRepository.save(partner);
                });
            }
        } else {
            throw new ConflictException("Nothing to unlink");
        }
    }

    public ReviewQueueResponse getReviewQueue(UUID userId) {
        List<BankTransaction> duplicates = transactionRepository.findDuplicatePairs(userId);
        List<ReviewQueueResponse.TransactionPair> dupPairs = duplicates.stream()
                .map(tx -> {
                    TransactionResponse main = TransactionResponse.fromEntity(tx);
                    TransactionResponse partner = transactionRepository.findById(tx.getReviewPartnerId())
                            .map(TransactionResponse::fromEntity)
                            .orElse(null);
                    return new ReviewQueueResponse.TransactionPair(main, partner);
                })
                .filter(p -> p.getPartner() != null)
                .toList();

        List<BankTransaction> selfTransfers = transactionRepository.findSelfTransferPairs(userId);
        List<ReviewQueueResponse.TransactionPair> selfPairs = selfTransfers.stream()
                .map(tx -> {
                    TransactionResponse main = TransactionResponse.fromEntity(tx);
                    TransactionResponse partner = transactionRepository.findById(tx.getReviewPartnerId())
                            .map(TransactionResponse::fromEntity)
                            .orElse(null);
                    return new ReviewQueueResponse.TransactionPair(main, partner);
                })
                .filter(p -> p.getPartner() != null)
                .toList();

        List<BankTransaction> refunds = transactionRepository.findRefundMatches(userId);
        List<ReviewQueueResponse.TransactionPair> refundPairs = refunds.stream()
                .map(tx -> {
                    TransactionResponse credit = TransactionResponse.fromEntity(tx);
                    TransactionResponse debit = transactionRepository.findById(tx.getReviewPartnerId())
                            .map(TransactionResponse::fromEntity)
                            .orElse(null);
                    return new ReviewQueueResponse.TransactionPair(credit, debit);
                })
                .filter(p -> p.getPartner() != null)
                .toList();

        List<UnparsedSmsQueue> unparsed = unparsedSmsQueueRepository.findByUserIdAndStatusOrderByReceivedAtDesc(userId, "pending");
        List<ReviewQueueResponse.UnparsedSmsItem> unparsedItems = unparsed.stream()
                .map(u -> new ReviewQueueResponse.UnparsedSmsItem(u.getId(), u.getRawText(), u.getSender(), u.getReceivedAt()))
                .toList();

        return new ReviewQueueResponse(dupPairs, selfPairs, refundPairs, unparsedItems);
    }

    @Transactional
    public void confirmReview(UUID userId, UUID id) {
        BankTransaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        String status = tx.getReviewStatus();
        if (status == null || "none".equals(status)) {
            throw new ConflictException("Transaction has no review flag");
        }

        switch (status) {
            case "possible_duplicate" -> {
                // DELETE this transaction using the delete steps in A4.2 (the partner stays, its flag is cleared by step 2)
                deleteTransaction(userId, id);
            }
            case "possible_self_transfer" -> {
                UUID partnerId = tx.getReviewPartnerId();
                BankTransaction partner = transactionRepository.findByIdAndUserId(partnerId, userId)
                        .orElseThrow(() -> new NotFoundException("Partner transaction not found"));

                tx.setSelfTransfer(true);
                tx.setSelfTransferMatchedId(partnerId);
                tx.setReviewStatus("none");
                tx.setReviewPartnerId(null);

                partner.setSelfTransfer(true);
                partner.setSelfTransferMatchedId(id);
                partner.setReviewStatus("none");
                partner.setReviewPartnerId(null);

                Optional<Category> transferCategory = categoryRepository.findFirstByUserIdIsNullAndNameIgnoreCase("Transfer");
                transferCategory.ifPresent(cat -> {
                    if (tx.getCategoryId() == null) {
                        tx.setCategoryId(cat.getId());
                    }
                    if (partner.getCategoryId() == null) {
                        partner.setCategoryId(cat.getId());
                    }
                });

                transactionRepository.save(tx);
                transactionRepository.save(partner);
            }
            case "possible_refund_match" -> {
                UUID debitId = tx.getReviewPartnerId();
                BankTransaction debit = transactionRepository.findByIdAndUserId(debitId, userId)
                        .orElseThrow(() -> new NotFoundException("Debit partner transaction not found"));

                tx.setRefundMatchedId(debitId);
                tx.setReviewStatus("none");
                tx.setReviewPartnerId(null);

                debit.setRefundMatchedId(id);

                transactionRepository.save(tx);
                transactionRepository.save(debit);
            }
            default -> throw new ConflictException("Unknown review status: " + status);
        }
    }

    @Transactional
    public void dismissReview(UUID userId, UUID id) {
        BankTransaction tx = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        UUID partnerId = tx.getReviewPartnerId();
        tx.setReviewStatus("none");
        tx.setReviewPartnerId(null);
        transactionRepository.save(tx);

        if (partnerId != null) {
            transactionRepository.findByIdAndUserId(partnerId, userId).ifPresent(partner -> {
                if (id.equals(partner.getReviewPartnerId())) {
                    partner.setReviewStatus("none");
                    partner.setReviewPartnerId(null);
                    transactionRepository.save(partner);
                }
            });
        }
    }
}
