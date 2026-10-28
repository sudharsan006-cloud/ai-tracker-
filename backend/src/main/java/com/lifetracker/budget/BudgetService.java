package com.lifetracker.budget;

import com.lifetracker.bank.CurrencyRepository;
import com.lifetracker.budget.dto.BudgetResponse;
import com.lifetracker.budget.dto.CreateBudgetRequest;
import com.lifetracker.budget.dto.PatchBudgetRequest;
import com.lifetracker.category.Category;
import com.lifetracker.category.CategoryRepository;
import com.lifetracker.common.ConflictException;
import com.lifetracker.common.NotFoundException;
import com.lifetracker.user.UserProfile;
import com.lifetracker.user.UserProfileRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CurrencyRepository currencyRepository;
    private final CategoryRepository categoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final JdbcTemplate jdbcTemplate;

    public BudgetService(
            BudgetRepository budgetRepository,
            CurrencyRepository currencyRepository,
            CategoryRepository categoryRepository,
            UserProfileRepository userProfileRepository,
            JdbcTemplate jdbcTemplate) {
        this.budgetRepository = budgetRepository;
        this.currencyRepository = currencyRepository;
        this.categoryRepository = categoryRepository;
        this.userProfileRepository = userProfileRepository;
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

    public List<BudgetResponse> getBudgets(UUID userId) {
        List<Budget> budgets = budgetRepository.findByUserIdAndIsActiveTrue(userId);
        ZoneId zoneId = getUserZoneId(userId);
        ZonedDateTime nowZoned = ZonedDateTime.now(zoneId);
        ZonedDateTime startZoned = nowZoned.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime endZoned = startZoned.plusMonths(1);

        Instant periodStart = startZoned.toInstant();
        Instant periodEnd = endZoned.toInstant();
        Timestamp startTs = Timestamp.from(periodStart);
        Timestamp endTs = Timestamp.from(periodEnd);

        return budgets.stream()
                .map(b -> computeBudgetStatus(b, userId, periodStart, periodEnd, startTs, endTs))
                .toList();
    }

    private BudgetResponse computeBudgetStatus(
            Budget budget,
            UUID userId,
            Instant periodStart,
            Instant periodEnd,
            Timestamp startTs,
            Timestamp endTs) {

        BigDecimal rawSum;
        if (budget.getCategoryId() == null) {
            String overallSql = """
                SELECT COALESCE(SUM(CASE WHEN t.direction = 'debit' THEN t.amount ELSE -t.amount END), 0)
                FROM bank_transactions t
                WHERE t.user_id = ? AND t.currency_id = ?
                  AND t.is_self_transfer = false
                  AND t.transaction_date >= ? AND t.transaction_date < ?
                  AND (t.direction = 'debit' OR t.refund_matched_id IS NOT NULL)
                """;
            rawSum = jdbcTemplate.queryForObject(overallSql, BigDecimal.class,
                    userId, budget.getCurrencyId(), startTs, endTs);
        } else {
            String catSql = """
                SELECT COALESCE(SUM(CASE WHEN t.direction = 'debit' THEN t.amount ELSE -t.amount END), 0)
                FROM bank_transactions t
                LEFT JOIN bank_transactions o ON o.id = t.refund_matched_id
                WHERE t.user_id = ? AND t.currency_id = ?
                  AND t.is_self_transfer = false
                  AND t.transaction_date >= ? AND t.transaction_date < ?
                  AND ( (t.direction = 'debit' AND t.category_id = ?)
                     OR (t.direction = 'credit' AND o.category_id = ?) )
                """;
            rawSum = jdbcTemplate.queryForObject(catSql, BigDecimal.class,
                    userId, budget.getCurrencyId(), startTs, endTs, budget.getCategoryId(), budget.getCategoryId());
        }

        if (rawSum == null) {
            rawSum = BigDecimal.ZERO;
        }

        BigDecimal spent = rawSum.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : rawSum.setScale(2, RoundingMode.HALF_UP);

        BigDecimal remaining = budget.getAmount().subtract(spent).setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentUsed = spent.multiply(BigDecimal.valueOf(100))
                .divide(budget.getAmount(), 1, RoundingMode.HALF_UP);
        boolean overBudget = spent.compareTo(budget.getAmount()) > 0;

        BudgetResponse resp = new BudgetResponse();
        resp.setId(budget.getId());
        resp.setCategoryId(budget.getCategoryId());
        resp.setCurrencyId(budget.getCurrencyId());
        resp.setAmount(budget.getAmount());
        resp.setPeriod(budget.getPeriod());
        resp.setSpent(spent);
        resp.setRemaining(remaining);
        resp.setPercentUsed(percentUsed);
        resp.setOverBudget(overBudget);
        resp.setPeriodStart(periodStart);
        resp.setPeriodEnd(periodEnd);

        return resp;
    }

    @Transactional
    public BudgetResponse createBudget(UUID userId, CreateBudgetRequest request) {
        if (!currencyRepository.existsById(request.getCurrencyId())) {
            throw new IllegalArgumentException("Invalid currencyId: " + request.getCurrencyId());
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            if (!category.isActive()) {
                throw new IllegalArgumentException("Category is inactive");
            }
            if (!"expense".equalsIgnoreCase(category.getType())) {
                throw new IllegalArgumentException("Budget category must be of type 'expense'");
            }
            if (category.getUserId() != null && !category.getUserId().equals(userId)) {
                throw new IllegalArgumentException("Category does not belong to user");
            }

            if (budgetRepository.existsByUserIdAndCategoryIdAndCurrencyIdAndIsActiveTrue(
                    userId, request.getCategoryId(), request.getCurrencyId())) {
                throw new ConflictException("An active budget already exists for this category and currency");
            }
        } else {
            if (budgetRepository.existsByUserIdAndCategoryIdIsNullAndCurrencyIdAndIsActiveTrue(
                    userId, request.getCurrencyId())) {
                throw new ConflictException("An active overall budget already exists for this currency");
            }
        }

        Budget budget = new Budget(userId, request.getCategoryId(), request.getCurrencyId(), request.getAmount());
        budget = budgetRepository.save(budget);

        ZoneId zoneId = getUserZoneId(userId);
        ZonedDateTime nowZoned = ZonedDateTime.now(zoneId);
        ZonedDateTime startZoned = nowZoned.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime endZoned = startZoned.plusMonths(1);
        Instant periodStart = startZoned.toInstant();
        Instant periodEnd = endZoned.toInstant();

        return computeBudgetStatus(budget, userId, periodStart, periodEnd, Timestamp.from(periodStart), Timestamp.from(periodEnd));
    }

    @Transactional
    public BudgetResponse patchBudget(UUID userId, UUID id, PatchBudgetRequest request) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Budget not found"));

        if (!budget.isActive()) {
            throw new NotFoundException("Budget not found");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }

        budget.setAmount(request.getAmount());
        budget = budgetRepository.save(budget);

        ZoneId zoneId = getUserZoneId(userId);
        ZonedDateTime nowZoned = ZonedDateTime.now(zoneId);
        ZonedDateTime startZoned = nowZoned.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime endZoned = startZoned.plusMonths(1);
        Instant periodStart = startZoned.toInstant();
        Instant periodEnd = endZoned.toInstant();

        return computeBudgetStatus(budget, userId, periodStart, periodEnd, Timestamp.from(periodStart), Timestamp.from(periodEnd));
    }

    @Transactional
    public void deleteBudget(UUID userId, UUID id) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Budget not found"));

        if (!budget.isActive()) {
            throw new NotFoundException("Budget not found");
        }

        budget.setActive(false);
        budgetRepository.save(budget);
    }
}
