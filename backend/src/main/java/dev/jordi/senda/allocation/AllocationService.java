package dev.jordi.senda.allocation;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryBalance;
import dev.jordi.senda.category.CategoryBalanceRepository;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Salary split over expense categories. The "plan" is the set of expense
 * categories that carry a {@code target_percentage}; distributing a paycheck
 * adds money to those categories' {@link CategoryBalance} envelopes — the very
 * same balances shown on the Categories screen.
 */
@Service
public class AllocationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final CategoryRepository categoryRepository;
    private final CategoryBalanceRepository balanceRepository;

    public AllocationService(CategoryRepository categoryRepository,
                             CategoryBalanceRepository balanceRepository) {
        this.categoryRepository = categoryRepository;
        this.balanceRepository = balanceRepository;
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * All active expense categories with their target percentage (0 when not in
     * the plan) and current envelope balance, ordered by name.
     */
    @Transactional(readOnly = true)
    public List<EnvelopeResponse> list(Long userId) {
        Map<Long, BigDecimal> balances = balanceByCategory(userId);
        return activeExpenseCategories(userId).stream()
                .map(c -> toResponse(c, balances))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Save plan
    // -------------------------------------------------------------------------

    /**
     * Sets the target percentage on the given expense categories and clears it on
     * every other expense category, so the plan is exactly the supplied list. The
     * supplied percentages MUST sum to exactly 100. Categories are authorized
     * before any mutation.
     */
    @Transactional
    public List<EnvelopeResponse> savePlan(Long userId, EnvelopePlanRequest request) {
        validateSum(request.envelopes().stream()
                .map(EnvelopeLineRequest::percentage)
                .toList());

        List<Category> expense = categoryRepository.findByUserId(userId).stream()
                .filter(c -> c.getType() == TransactionType.EXPENSE)
                .toList();
        Map<Long, Category> byId = expense.stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        // Authorize every supplied id BEFORE any mutation. A foreign or unknown id
        // (or a non-expense category) fails here, before we change anything.
        for (EnvelopeLineRequest line : request.envelopes()) {
            if (!byId.containsKey(line.categoryId())) {
                throw new NotFoundException("Category not found");
            }
        }

        Set<Long> incoming = request.envelopes().stream()
                .map(EnvelopeLineRequest::categoryId)
                .collect(Collectors.toSet());

        // Clear target on categories no longer in the plan
        for (Category c : expense) {
            if (!incoming.contains(c.getId())) {
                c.setTargetPercentage(null);
            }
        }
        // Apply the plan and make sure each has a balance row (idempotent)
        for (EnvelopeLineRequest line : request.envelopes()) {
            byId.get(line.categoryId()).setTargetPercentage(line.percentage());
            if (balanceRepository.findByCategoryId(line.categoryId()).isEmpty()) {
                balanceRepository.save(new CategoryBalance(line.categoryId(), userId));
            }
        }
        // Dirty checking flushes the target_percentage updates on commit
        return list(userId);
    }

    // -------------------------------------------------------------------------
    // Distribute (simulate or persist)
    // -------------------------------------------------------------------------

    /**
     * Distributes {@code amount} across the plan's categories proportionally to
     * their target percentages. Each line is rounded HALF_UP to 2 decimals and
     * the last category absorbs the rounding residual so the sum is exact. When
     * {@code persist} is true the shares are added to each category's balance.
     */
    @Transactional
    public DistributionResponse distribute(Long userId, DistributeRequest request) {
        List<Category> plan = activeExpenseCategories(userId).stream()
                .filter(c -> c.getTargetPercentage() != null)
                .toList();

        if (plan.isEmpty()) {
            throw new InvalidAllocationException("No allocation plan defined. Save a plan first.");
        }
        validateSum(plan.stream().map(Category::getTargetPercentage).toList());

        BigDecimal amount = request.amount();
        List<BigDecimal> rawAllocations = plan.stream()
                .map(c -> amount.multiply(c.getTargetPercentage())
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP))
                .toList();

        BigDecimal allocated = rawAllocations.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal residual = amount.subtract(allocated);

        List<BigDecimal> finalAllocations = new ArrayList<>(rawAllocations);
        if (residual.compareTo(BigDecimal.ZERO) != 0) {
            int last = finalAllocations.size() - 1;
            finalAllocations.set(last, finalAllocations.get(last).add(residual));
        }

        Map<Long, CategoryBalance> balanceByCategory = balanceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(CategoryBalance::getCategoryId, b -> b));

        List<DistributionLine> lines = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            Category category = plan.get(i);
            BigDecimal share = finalAllocations.get(i);

            CategoryBalance row = balanceByCategory.get(category.getId());
            if (row == null) {
                row = new CategoryBalance(category.getId(), userId);
                balanceByCategory.put(category.getId(), row);
            }

            BigDecimal newBalance;
            if (request.persist()) {
                row.setBalance(row.getBalance().add(share));
                balanceRepository.save(row);
                newBalance = row.getBalance();
            } else {
                newBalance = row.getBalance();
            }

            lines.add(new DistributionLine(
                    category.getId(),
                    category.getName(),
                    category.getTargetPercentage(),
                    share,
                    newBalance));
        }

        return new DistributionResponse(amount, lines);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private List<Category> activeExpenseCategories(Long userId) {
        return categoryRepository.findByUserIdAndActiveTrue(userId).stream()
                .filter(c -> c.getType() == TransactionType.EXPENSE)
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Category::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private Map<Long, BigDecimal> balanceByCategory(Long userId) {
        return balanceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(CategoryBalance::getCategoryId, CategoryBalance::getBalance));
    }

    private static EnvelopeResponse toResponse(Category c, Map<Long, BigDecimal> balances) {
        return new EnvelopeResponse(
                c.getId(),
                c.getName(),
                c.getColor(),
                c.getTargetPercentage() != null ? c.getTargetPercentage() : ZERO,
                balances.getOrDefault(c.getId(), ZERO));
    }

    private static void validateSum(List<BigDecimal> percentages) {
        BigDecimal sum = percentages.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(HUNDRED) != 0) {
            throw new ConflictException(
                    "Allocation percentages must sum to exactly 100 (current sum: " + sum.toPlainString() + ")");
        }
    }
}
