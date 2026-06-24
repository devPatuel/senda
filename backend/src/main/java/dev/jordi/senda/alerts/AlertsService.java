package dev.jordi.senda.alerts;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.recurring.RecurringPayment;
import dev.jordi.senda.recurring.RecurringPaymentRepository;
import dev.jordi.senda.transaction.CategoryExpenseStat;
import dev.jordi.senda.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Heuristic home-screen alerts. Both rules are intentionally conservative so the
 * section stays empty unless there is something genuinely worth surfacing.
 */
@Service
public class AlertsService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    // "Ant expenses": at least this many purchases, averaging at most MAX_ANT_AVG,
    // adding up to at least MIN_ANT_SUM this month.
    private static final long MIN_ANT_COUNT = 8;
    private static final BigDecimal MAX_ANT_AVG = new BigDecimal("10");
    private static final BigDecimal MIN_ANT_SUM = new BigDecimal("50");

    private final TransactionRepository transactionRepository;
    private final RecurringPaymentRepository recurringPaymentRepository;
    private final CategoryRepository categoryRepository;

    public AlertsService(TransactionRepository transactionRepository,
                         RecurringPaymentRepository recurringPaymentRepository,
                         CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.recurringPaymentRepository = recurringPaymentRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public AlertsResponse alerts(Long userId) {
        return new AlertsResponse(antExpenses(userId), forgottenSubscriptions(userId));
    }

    private List<AntExpenseAlert> antExpenses(Long userId) {
        LocalDate from = LocalDate.now(ZONE).withDayOfMonth(1);
        LocalDate to = from.plusMonths(1).minusDays(1);
        return transactionRepository.expenseStatsByCategory(userId, from, to).stream()
                .filter(AlertsService::isAntExpense)
                .map(s -> new AntExpenseAlert(s.categoryId(), s.categoryName(), s.categoryColor(),
                        s.count(), s.total()))
                .toList();
    }

    private static boolean isAntExpense(CategoryExpenseStat s) {
        if (s.count() < MIN_ANT_COUNT || s.total().compareTo(MIN_ANT_SUM) < 0) {
            return false;
        }
        // avg <= MAX_ANT_AVG  <=>  total <= count * MAX_ANT_AVG (no division)
        BigDecimal cap = MAX_ANT_AVG.multiply(BigDecimal.valueOf(s.count()));
        return s.total().compareTo(cap) <= 0;
    }

    private List<ForgottenSubscriptionAlert> forgottenSubscriptions(Long userId) {
        List<RecurringPayment> payments = recurringPaymentRepository.findByUserId(userId);
        if (payments.isEmpty()) {
            return List.of();
        }
        LocalDate from = LocalDate.now(ZONE).minusMonths(2);
        Set<Long> usedCategories = Set.copyOf(transactionRepository.categoryIdsWithExpenseSince(userId, from));
        Map<Long, Category> categories = categoryRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        return payments.stream()
                .filter(p -> !usedCategories.contains(p.getCategoryId()))
                .map(p -> {
                    Category c = categories.get(p.getCategoryId());
                    return new ForgottenSubscriptionAlert(
                            p.getId(), p.getName(), p.getCategoryId(),
                            c != null ? c.getName() : null,
                            c != null ? c.getColor() : null);
                })
                .toList();
    }
}
