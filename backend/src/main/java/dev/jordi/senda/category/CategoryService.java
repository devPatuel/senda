package dev.jordi.senda.category;

import dev.jordi.senda.account.AccountRepository;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import dev.jordi.senda.transaction.CategorySpent;
import dev.jordi.senda.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryBalanceRepository categoryBalanceRepository;
    private final AccountRepository accountRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           TransactionRepository transactionRepository,
                           CategoryBalanceRepository categoryBalanceRepository,
                           AccountRepository accountRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.categoryBalanceRepository = categoryBalanceRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId, TransactionType type, boolean includeInactive) {
        List<Category> categories = includeInactive
                ? categoryRepository.findByUserId(userId)
                : categoryRepository.findByUserIdAndActiveTrue(userId);
        return categories.stream()
                .filter(category -> type == null || category.getType() == type)
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Category::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        if (categoryRepository.existsByUserIdAndNameAndType(userId, request.name(), request.type())) {
            throw new ConflictException("Category already exists for this type");
        }
        Category saved = categoryRepository.save(
                new Category(userId, request.name(), request.type(), request.color()));
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(Long userId, Long id, CategoryUpdateRequest request) {
        Category category = findOwned(userId, id);
        boolean renamed = !category.getName().equals(request.name());
        if (renamed && categoryRepository.existsByUserIdAndNameAndType(
                userId, request.name(), category.getType())) {
            throw new ConflictException("Category already exists for this type");
        }
        category.setName(request.name());
        category.setColor(request.color());
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Category category = findOwned(userId, id);
        // A category with transactions is never removed: deactivate to keep history intact
        if (transactionRepository.existsByCategoryId(category.getId())) {
            category.setActive(false);
            categoryRepository.save(category);
        } else {
            categoryRepository.delete(category);
        }
    }

    // -------------------------------------------------------------------------
    // Budget (envelopes over expense categories)
    // -------------------------------------------------------------------------

    /**
     * Budget overview: each active expense category with its envelope balance and
     * this month's spend, plus the summary. "To assign" is derived as
     * {@code totalAccounts - totalAssigned}, so the total-vs-categories invariant
     * always holds. {@code totalAssigned} sums ALL of the user's category balances
     * (including inactive ones, whose money is still assigned).
     */
    @Transactional(readOnly = true)
    public CategoryBudgetResponse budget(Long userId) {
        Map<Long, CategoryBalance> balances = categoryBalanceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(CategoryBalance::getCategoryId, b -> b));

        LocalDate from = LocalDate.now().withDayOfMonth(1);
        LocalDate to = from.plusMonths(1).minusDays(1);
        Map<Long, BigDecimal> spent = transactionRepository.sumExpenseByCategory(userId, from, to).stream()
                .collect(Collectors.toMap(CategorySpent::categoryId, CategorySpent::spent));

        List<CategoryBudgetLine> lines = categoryRepository.findByUserIdAndActiveTrue(userId).stream()
                .filter(c -> c.getType() == TransactionType.EXPENSE)
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Category::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(c -> {
                    CategoryBalance b = balances.get(c.getId());
                    return new CategoryBudgetLine(
                            c.getId(),
                            c.getName(),
                            c.getColor(),
                            b != null ? b.getBalance() : ZERO,
                            spent.getOrDefault(c.getId(), ZERO),
                            c.getTargetPercentage(),
                            b != null ? b.getTargetAmount() : null);
                })
                .toList();

        BigDecimal totalAccounts = accountRepository.sumActiveBalance(userId);
        if (totalAccounts == null) {
            totalAccounts = ZERO;
        }
        BigDecimal totalAssigned = balances.values().stream()
                .map(CategoryBalance::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        BigDecimal toAssign = totalAccounts.subtract(totalAssigned);

        return new CategoryBudgetResponse(totalAccounts, totalAssigned, toAssign, lines);
    }

    /**
     * Adjusts an expense category's envelope balance by a (possibly negative)
     * delta. Creates the balance row lazily if it does not exist yet. Returns the
     * refreshed budget so the caller can update the whole view in one round-trip.
     */
    @Transactional
    public CategoryBudgetResponse assign(Long userId, Long categoryId, AssignRequest request) {
        Category category = findOwned(userId, categoryId);
        if (category.getType() != TransactionType.EXPENSE) {
            throw new ConflictException("Only expense categories can hold a balance");
        }
        CategoryBalance balance = categoryBalanceRepository.findByCategoryId(categoryId)
                .orElseGet(() -> new CategoryBalance(categoryId, userId));
        balance.setBalance(balance.getBalance().add(request.amount()));
        categoryBalanceRepository.save(balance);
        return budget(userId);
    }

    /**
     * Sets (or clears, with a null amount) the funding target of an expense
     * category's envelope. Creates the balance row lazily if needed. Returns the
     * refreshed budget so the caller can update the whole view in one round-trip.
     */
    @Transactional
    public CategoryBudgetResponse setTarget(Long userId, Long categoryId, TargetRequest request) {
        Category category = findOwned(userId, categoryId);
        if (category.getType() != TransactionType.EXPENSE) {
            throw new ConflictException("Only expense categories can hold a target");
        }
        CategoryBalance balance = categoryBalanceRepository.findByCategoryId(categoryId)
                .orElseGet(() -> new CategoryBalance(categoryId, userId));
        balance.setTargetAmount(request.targetAmount());
        categoryBalanceRepository.save(balance);
        return budget(userId);
    }

    private Category findOwned(Long userId, Long id) {
        // 404 (not 403) for another user's category: do not reveal its existence
        return categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }
}
