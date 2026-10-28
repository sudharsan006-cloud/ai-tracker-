package com.lifetracker.ai;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lifetracker.ai.dto.*;
import com.lifetracker.bank.Currency;
import com.lifetracker.bank.CurrencyRepository;
import com.lifetracker.budget.BudgetService;
import com.lifetracker.budget.dto.BudgetResponse;
import com.lifetracker.category.Category;
import com.lifetracker.category.CategoryRepository;
import com.lifetracker.config.ConfigService;
import com.lifetracker.fitness.FitnessService;
import com.lifetracker.fitness.dto.GoalsResponse;
import com.lifetracker.fitness.dto.RangeDayResponse;
import com.lifetracker.fitness.dto.RangeResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;

@Service
public class FactsService {

    private final JdbcTemplate jdbcTemplate;
    private final CurrencyRepository currencyRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetService budgetService;
    private final FitnessService fitnessService;
    private final ConfigService configService;
    private final AiSettingsService aiSettingsService;
    private final ObjectMapper canonicalMapper;

    public FactsService(
            JdbcTemplate jdbcTemplate,
            CurrencyRepository currencyRepository,
            CategoryRepository categoryRepository,
            BudgetService budgetService,
            FitnessService fitnessService,
            ConfigService configService,
            AiSettingsService aiSettingsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.currencyRepository = currencyRepository;
        this.categoryRepository = categoryRepository;
        this.budgetService = budgetService;
        this.fitnessService = fitnessService;
        this.configService = configService;
        this.aiSettingsService = aiSettingsService;

        this.canonicalMapper = new ObjectMapper()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public ZoneId getUserZoneId(UUID userId) {
        try {
            String tz = jdbcTemplate.queryForObject(
                    "SELECT timezone FROM user_profile WHERE user_id = ?", String.class, userId);
            if (tz != null && !tz.isBlank()) {
                return ZoneId.of(tz);
            }
        } catch (Exception ignored) {
        }
        return ZoneId.of("Asia/Kolkata");
    }

    public String toCanonicalJson(Object factsObject) {
        try {
            return canonicalMapper.writeValueAsString(factsObject);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize facts to canonical JSON", e);
        }
    }

    public String computeFactsHash(String canonicalJson) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate SHA-256 facts hash", e);
        }
    }

    public SpendingMonthFacts getSpendingMonthFacts(UUID userId) {
        ZoneId zone = getUserZoneId(userId);
        LocalDate today = LocalDate.now(zone);

        int daysElapsed = today.getDayOfMonth();
        int daysInMonth = today.lengthOfMonth();
        String periodLabel = today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + today.getYear() + ", days 1 to " + daysElapsed;

        Instant start = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        Timestamp startTs = Timestamp.from(start);
        Timestamp endTs = Timestamp.from(end);

        Currency currency = resolveCurrency(userId, startTs, endTs);

        // Current totals
        Totals totals = queryTotals(userId, currency.getId(), startTs, endTs);
        BigDecimal totalSpent = totals.totalSpent;
        BigDecimal totalIncome = totals.totalIncome;
        int transactionCount = totals.count;

        // Previous period
        LocalDate prevMonthDate = today.minusMonths(1);
        int prevDaysElapsed = Math.min(daysElapsed, prevMonthDate.lengthOfMonth());
        Instant prevStart = prevMonthDate.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant prevEnd = prevMonthDate.withDayOfMonth(prevDaysElapsed).plusDays(1).atStartOfDay(zone).toInstant();
        Timestamp prevStartTs = Timestamp.from(prevStart);
        Timestamp prevEndTs = Timestamp.from(prevEnd);

        Totals prevTotals = queryTotals(userId, currency.getId(), prevStartTs, prevEndTs);
        BigDecimal previousPeriodSpent = prevTotals.totalSpent;

        BigDecimal changeAmount = totalSpent.subtract(previousPeriodSpent).setScale(2, RoundingMode.HALF_UP);
        String changePercent = null;
        if (previousPeriodSpent.compareTo(BigDecimal.ZERO) > 0) {
            changePercent = changeAmount.multiply(BigDecimal.valueOf(100))
                    .divide(previousPeriodSpent, 1, RoundingMode.HALF_UP)
                    .toPlainString();
        }

        // Top categories
        List<TopCategoryFact> topCategories = new ArrayList<>();
        if (totalSpent.compareTo(BigDecimal.ZERO) > 0) {
            topCategories = queryTopCategories(userId, currency.getId(), startTs, endTs, totalSpent, 5);
        }

        // Budgets
        List<BudgetFact> budgetFacts = new ArrayList<>();
        List<BudgetResponse> allBudgets = budgetService.getBudgets(userId);
        for (BudgetResponse b : allBudgets) {
            if (Objects.equals(b.getCurrencyId(), currency.getId())) {
                String label;
                if (b.getCategoryId() == null) {
                    label = "Overall";
                } else {
                    label = categoryRepository.findById(b.getCategoryId())
                            .map(Category::getName)
                            .orElse("Uncategorized");
                }
                budgetFacts.add(new BudgetFact(
                        label,
                        b.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        b.getSpent().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        b.getPercentUsed().setScale(1, RoundingMode.HALF_UP).toPlainString(),
                        b.isOverBudget()
                ));
            }
        }

        SpendingMonthFacts facts = new SpendingMonthFacts();
        facts.setKind("spending_month");
        facts.setPeriodLabel(periodLabel);
        facts.setCurrency(currency.getCode());
        facts.setCurrencySymbol(currency.getSymbol());
        facts.setDaysElapsed(daysElapsed);
        facts.setDaysInMonth(daysInMonth);
        facts.setTransactionCount(transactionCount);
        facts.setTotalSpent(totalSpent.setScale(2, RoundingMode.HALF_UP).toPlainString());
        facts.setTotalIncome(totalIncome.setScale(2, RoundingMode.HALF_UP).toPlainString());
        facts.setPreviousPeriodSpent(previousPeriodSpent.setScale(2, RoundingMode.HALF_UP).toPlainString());
        facts.setChangeAmount(changeAmount.toPlainString());
        facts.setChangePercent(changePercent);
        facts.setTopCategories(topCategories);
        facts.setBudgets(budgetFacts);

        return facts;
    }

    public FitnessWeekFacts getFitnessWeekFacts(UUID userId) {
        ZoneId zone = getUserZoneId(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(6);
        LocalDate to = today;

        GoalsResponse goals = fitnessService.getGoals(userId);
        RangeResponse range = fitnessService.getRange(userId, from.toString(), to.toString());

        int daysWithData = 0;
        int daysStepsGoalMet = 0;
        int daysWaterGoalMet = 0;
        int workoutCount = 0;
        int caloriesBurnedEstTotal = 0;

        BestStepsDay bestStepsDay = null;
        int maxSteps = 0;

        for (RangeDayResponse d : range.getDays()) {
            if (d.getSteps() > 0 || d.getWaterMl() > 0 || d.getCaloriesIn() > 0 || d.getWorkoutCount() > 0) {
                daysWithData++;
            }
            if (d.getSteps() >= goals.getStepsGoal()) {
                daysStepsGoalMet++;
            }
            if (d.getWaterMl() >= goals.getWaterGoalMl()) {
                daysWaterGoalMet++;
            }
            workoutCount += d.getWorkoutCount();
            caloriesBurnedEstTotal += d.getCaloriesBurnedEst();

            if (d.getSteps() > maxSteps) {
                maxSteps = d.getSteps();
                bestStepsDay = new BestStepsDay(d.getDate(), d.getSteps());
            }
        }

        // workoutTotalMinutes sum from workouts in [from, to]
        String workoutMinSql = """
            SELECT COALESCE(SUM(duration_min), 0)
            FROM workouts
            WHERE user_id = ? AND workout_date >= ? AND workout_date <= ?
        """;
        Integer workoutTotalMinutes = jdbcTemplate.queryForObject(
                workoutMinSql, Integer.class, userId, Date.valueOf(from), Date.valueOf(to));
        if (workoutTotalMinutes == null) {
            workoutTotalMinutes = 0;
        }

        FitnessWeekFacts facts = new FitnessWeekFacts();
        facts.setKind("fitness_week");
        facts.setPeriodLabel("last 7 days up to " + today);
        facts.setPeriodDays(7);
        facts.setDaysWithData(daysWithData);
        facts.setStepsGoal(goals.getStepsGoal());
        facts.setWaterGoalMl(goals.getWaterGoalMl());
        facts.setAverageSteps(range.getAverages().getSteps());
        facts.setAverageWaterMl(range.getAverages().getWaterMl());
        facts.setAverageCaloriesIn(range.getAverages().getCaloriesIn());
        facts.setDaysStepsGoalMet(daysStepsGoalMet);
        facts.setDaysWaterGoalMet(daysWaterGoalMet);
        facts.setWorkoutCount(workoutCount);
        facts.setWorkoutTotalMinutes(workoutTotalMinutes);
        facts.setCaloriesBurnedEstTotal(caloriesBurnedEstTotal);
        facts.setBestStepsDay(bestStepsDay);

        return facts;
    }

    public OverviewWeekFacts getOverviewWeekFacts(UUID userId) {
        ZoneId zone = getUserZoneId(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(6);
        LocalDate to = today;

        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        Timestamp startTs = Timestamp.from(start);
        Timestamp endTs = Timestamp.from(end);

        Currency currency = resolveCurrency(userId, startTs, endTs);
        Totals totals = queryTotals(userId, currency.getId(), startTs, endTs);

        TopSpendingCategoryFact topSpendingCategory = null;
        if (totals.totalSpent.compareTo(BigDecimal.ZERO) > 0) {
            List<TopCategoryFact> topList = queryTopCategories(userId, currency.getId(), startTs, endTs, totals.totalSpent, 1);
            if (!topList.isEmpty()) {
                topSpendingCategory = new TopSpendingCategoryFact(topList.get(0).getName(), topList.get(0).getSpent());
            }
        }

        // Fitness numbers
        FitnessWeekFacts fit = getFitnessWeekFacts(userId);

        // Mood
        MoodOverviewFact moodFact = null;
        boolean includeMood = aiSettingsService.getSettings(userId).isIncludeMood();
        if (includeMood) {
            short lowMax = configService.get("mood_nudge_low_score_max").shortValue();
            String moodSql = """
                SELECT count(*) AS cnt,
                       COALESCE(AVG(score), 0) AS avg_score,
                       COUNT(CASE WHEN score <= ? THEN 1 END) AS low_days
                FROM mood_log
                WHERE user_id = ? AND log_date >= ? AND log_date <= ?
            """;
            moodFact = jdbcTemplate.query(moodSql, rs -> {
                if (rs.next()) {
                    int cnt = rs.getInt("cnt");
                    if (cnt > 0) {
                        BigDecimal avg = rs.getBigDecimal("avg_score").setScale(1, RoundingMode.HALF_UP);
                        int lowDays = rs.getInt("low_days");
                        return new MoodOverviewFact(cnt, avg.toPlainString(), lowDays);
                    }
                }
                return null;
            }, lowMax, userId, Date.valueOf(from), Date.valueOf(to));
        }

        OverviewWeekFacts facts = new OverviewWeekFacts();
        facts.setKind("overview_week");
        facts.setPeriodLabel("last 7 days up to " + today);
        facts.setPeriodDays(7);
        facts.setCurrency(currency.getCode());
        facts.setCurrencySymbol(currency.getSymbol());
        facts.setTransactionCount(totals.count);
        facts.setSpentLast7Days(totals.totalSpent.setScale(2, RoundingMode.HALF_UP).toPlainString());
        facts.setTopSpendingCategory(topSpendingCategory);
        facts.setDaysWithFitnessData(fit.getDaysWithData());
        facts.setAverageSteps(fit.getAverageSteps());
        facts.setAverageWaterMl(fit.getAverageWaterMl());
        facts.setWorkoutCount(fit.getWorkoutCount());
        facts.setMood(moodFact);

        return facts;
    }

    private Currency resolveCurrency(UUID userId, Timestamp start, Timestamp end) {
        String sql = """
            SELECT currency_id, COUNT(*) AS cnt
            FROM bank_transactions
            WHERE user_id = ? AND is_self_transfer = false
              AND transaction_date >= ? AND transaction_date < ?
            GROUP BY currency_id
            ORDER BY cnt DESC
            LIMIT 1
        """;
        try {
            Integer currencyId = jdbcTemplate.queryForObject(sql, Integer.class, userId, start, end);
            if (currencyId != null) {
                return currencyRepository.findById(currencyId).orElseGet(this::getDefaultCurrency);
            }
        } catch (EmptyResultDataAccessException ignored) {
        }

        // Try user's active bank account currency
        try {
            Integer bankCurrencyId = jdbcTemplate.queryForObject(
                    "SELECT currency_id FROM bank_accounts WHERE user_id = ? AND is_active = true LIMIT 1",
                    Integer.class, userId);
            if (bankCurrencyId != null) {
                return currencyRepository.findById(bankCurrencyId).orElseGet(this::getDefaultCurrency);
            }
        } catch (EmptyResultDataAccessException ignored) {
        }

        return getDefaultCurrency();
    }

    private Currency getDefaultCurrency() {
        return currencyRepository.findByCode("INR")
                .orElseGet(() -> currencyRepository.findAll().stream().findFirst()
                        .orElse(new Currency("INR", "Indian Rupee", "₹", (short) 2)));
    }

    private static class Totals {
        final BigDecimal totalSpent;
        final BigDecimal totalIncome;
        final int count;

        Totals(BigDecimal totalSpent, BigDecimal totalIncome, int count) {
            this.totalSpent = totalSpent;
            this.totalIncome = totalIncome;
            this.count = count;
        }
    }

    private Totals queryTotals(UUID userId, Integer currencyId, Timestamp start, Timestamp end) {
        String sql = """
            SELECT
              COALESCE(SUM(CASE WHEN t.direction='debit' THEN t.amount END),0) AS debits,
              COALESCE(SUM(CASE WHEN t.direction='credit' AND t.refund_matched_id IS NOT NULL THEN t.amount END),0) AS refunds,
              COALESCE(SUM(CASE WHEN t.direction='credit' AND t.refund_matched_id IS NULL THEN t.amount END),0) AS income,
              COUNT(*) AS cnt
            FROM bank_transactions t
            WHERE t.user_id = ? AND t.currency_id = ? AND t.is_self_transfer = false
              AND t.transaction_date >= ? AND t.transaction_date < ?
        """;

        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                BigDecimal debits = rs.getBigDecimal("debits");
                BigDecimal refunds = rs.getBigDecimal("refunds");
                BigDecimal income = rs.getBigDecimal("income");
                int cnt = rs.getInt("cnt");

                BigDecimal netSpent = debits.subtract(refunds);
                BigDecimal totalSpent = netSpent.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : netSpent;
                BigDecimal totalIncome = income.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : income;

                return new Totals(totalSpent, totalIncome, cnt);
            }
            return new Totals(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }, userId, currencyId, start, end);
    }

    private List<TopCategoryFact> queryTopCategories(UUID userId, Integer currencyId, Timestamp start, Timestamp end, BigDecimal totalSpent, int limit) {
        String sql = """
            SELECT COALESCE(c.name, 'Uncategorized') AS name, SUM(x.signed) AS spent
            FROM (
              SELECT t.category_id AS category_id, t.amount AS signed
                FROM bank_transactions t
               WHERE t.user_id = ? AND t.currency_id = ? AND t.is_self_transfer = false
                 AND t.direction = 'debit'
                 AND t.transaction_date >= ? AND t.transaction_date < ?
              UNION ALL
              SELECT o.category_id AS category_id, -t.amount AS signed
                FROM bank_transactions t JOIN bank_transactions o ON o.id = t.refund_matched_id
               WHERE t.user_id = ? AND t.currency_id = ? AND t.is_self_transfer = false
                 AND t.direction = 'credit'
                 AND t.transaction_date >= ? AND t.transaction_date < ?
            ) x
            LEFT JOIN categories c ON c.id = x.category_id
            GROUP BY x.category_id, c.name
            HAVING SUM(x.signed) > 0
            ORDER BY spent DESC
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String name = rs.getString("name");
            BigDecimal spent = rs.getBigDecimal("spent").setScale(2, RoundingMode.HALF_UP);
            String sharePercent = spent.multiply(BigDecimal.valueOf(100))
                    .divide(totalSpent, 1, RoundingMode.HALF_UP)
                    .toPlainString();
            return new TopCategoryFact(name, spent.toPlainString(), sharePercent);
        }, userId, currencyId, start, end, userId, currencyId, start, end, limit);
    }
}
