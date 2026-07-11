package dev.jordi.senda.category;

import dev.jordi.senda.account.AccountRepository;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import dev.jordi.senda.space.SpaceAccess;
import dev.jordi.senda.transaction.CategorySpent;
import dev.jordi.senda.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryBalanceRepository categoryBalanceRepository;
    private final AccountRepository accountRepository;
    private final SpaceAccess spaceAccess;

    public CategoryService(CategoryRepository categoryRepository,
                           TransactionRepository transactionRepository,
                           CategoryBalanceRepository categoryBalanceRepository,
                           AccountRepository accountRepository,
                           SpaceAccess spaceAccess) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.categoryBalanceRepository = categoryBalanceRepository;
        this.accountRepository = accountRepository;
        this.spaceAccess = spaceAccess;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId, Long spaceId, TransactionType type, boolean includeInactive) {
        List<Category> categories;
        if (spaceId == null) {
            categories = includeInactive
                    ? categoryRepository.findByUserIdAndSpaceIdIsNull(userId)
                    : categoryRepository.findByUserIdAndSpaceIdIsNullAndActiveTrue(userId);
        } else {
            spaceAccess.assertActiveMember(userId, spaceId);
            categories = includeInactive
                    ? categoryRepository.findBySpaceId(spaceId)
                    : categoryRepository.findBySpaceIdAndActiveTrue(spaceId);
        }
        return categories.stream()
                .filter(category -> type == null || category.getType() == type)
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Category::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        Long spaceId = request.spaceId();
        if (spaceId == null) {
            if (categoryRepository.existsByUserIdAndNameAndTypeAndSpaceIdIsNull(
                    userId, request.name(), request.type())) {
                throw new ConflictException("Category already exists for this type");
            }
        } else {
            spaceAccess.assertActiveMember(userId, spaceId);
            if (categoryRepository.existsBySpaceIdAndNameAndType(spaceId, request.name(), request.type())) {
                throw new ConflictException("Category already exists for this type");
            }
        }
        Category category = new Category(userId, request.name(), request.type(), request.color());
        category.setSpaceId(spaceId);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long userId, Long id, CategoryUpdateRequest request) {
        Category category = findAccessible(userId, id);
        boolean renamed = !category.getName().equals(request.name());
        if (renamed && isDuplicate(userId, category, request.name())) {
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
        Category category = findAccessible(userId, id);
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
    public CategoryBudgetResponse budget(Long userId, Long spaceId) {
        LocalDate from = LocalDate.now().withDayOfMonth(1);
        LocalDate to = from.plusMonths(1).minusDays(1);

        List<Category> expenseCategories;
        Map<Long, CategoryBalance> balances;
        Map<Long, BigDecimal> spent;
        BigDecimal totalAccounts;

        if (spaceId == null) {
            expenseCategories = categoryRepository.findByUserIdAndSpaceIdIsNullAndActiveTrue(userId).stream()
                    .filter(c -> c.getType() == TransactionType.EXPENSE).toList();
            // Envelopes are keyed by category and owned by the user who first assigned to
            // them, so a couple-category envelope also carries this userId. Restrict the
            // personal budget to envelopes of personal (space_id IS NULL) categories,
            // including inactive ones, so shared money never leaks into the personal view.
            Set<Long> personalCategoryIds = categoryRepository.findByUserIdAndSpaceIdIsNull(userId).stream()
                    .map(Category::getId).collect(Collectors.toSet());
            balances = categoryBalanceRepository.findByUserId(userId).stream()
                    .filter(b -> personalCategoryIds.contains(b.getCategoryId()))
                    .collect(Collectors.toMap(CategoryBalance::getCategoryId, b -> b));
            spent = transactionRepository.sumExpenseByCategory(userId, from, to).stream()
                    .collect(Collectors.toMap(CategorySpent::categoryId, CategorySpent::spent));
            totalAccounts = accountRepository.sumActiveBalance(userId);
        } else {
            spaceAccess.assertActiveMember(userId, spaceId);
            expenseCategories = categoryRepository.findBySpaceIdAndActiveTrue(spaceId).stream()
                    .filter(c -> c.getType() == TransactionType.EXPENSE).toList();
            // Balances cover ALL of the space's categories (including inactive ones,
            // whose money is still assigned), so totalAssigned matches the personal
            // path; lines below still show only the active categories.
            List<Long> ids = categoryRepository.findBySpaceId(spaceId).stream()
                    .map(Category::getId).toList();
            balances = (ids.isEmpty() ? List.<CategoryBalance>of() : categoryBalanceRepository.findByCategoryIdIn(ids)).stream()
                    .collect(Collectors.toMap(CategoryBalance::getCategoryId, b -> b));
            spent = transactionRepository.sumExpenseByCategoryForSpace(spaceId, from, to).stream()
                    .collect(Collectors.toMap(CategorySpent::categoryId, CategorySpent::spent));
            totalAccounts = accountRepository.sumActiveBalanceBySpaceIds(List.of(spaceId));
        }
        if (totalAccounts == null) {
            totalAccounts = ZERO;
        }

        List<CategoryBudgetLine> lines = expenseCategories.stream()
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
    public CategoryBudgetResponse assign(Long userId, Long categoryId, Long spaceId, AssignRequest request) {
        Category category = findAccessible(userId, categoryId);
        if (category.getType() != TransactionType.EXPENSE) {
            throw new ConflictException("Only expense categories can hold a balance");
        }
        CategoryBalance balance = categoryBalanceRepository.findByCategoryId(categoryId)
                .orElseGet(() -> new CategoryBalance(categoryId, userId));
        balance.setBalance(balance.getBalance().add(request.amount()));
        categoryBalanceRepository.save(balance);
        return budget(userId, spaceId);
    }

    /**
     * Sets (or clears, with a null amount) the funding target of an expense
     * category's envelope. Creates the balance row lazily if needed. Returns the
     * refreshed budget so the caller can update the whole view in one round-trip.
     */
    @Transactional
    public CategoryBudgetResponse setTarget(Long userId, Long categoryId, Long spaceId, TargetRequest request) {
        Category category = findAccessible(userId, categoryId);
        if (category.getType() != TransactionType.EXPENSE) {
            throw new ConflictException("Only expense categories can hold a target");
        }
        CategoryBalance balance = categoryBalanceRepository.findByCategoryId(categoryId)
                .orElseGet(() -> new CategoryBalance(categoryId, userId));
        balance.setTargetAmount(request.targetAmount());
        categoryBalanceRepository.save(balance);
        return budget(userId, spaceId);
    }

    private boolean isDuplicate(Long userId, Category category, String name) {
        // Uniqueness is scoped: per-user for personal categories, per-space for shared ones.
        return category.getSpaceId() == null
                ? categoryRepository.existsByUserIdAndNameAndTypeAndSpaceIdIsNull(userId, name, category.getType())
                : categoryRepository.existsBySpaceIdAndNameAndType(category.getSpaceId(), name, category.getType());
    }

    /**
     * Loads a category the caller may act on: a personal category they own, or a
     * shared category of a space they are an ACTIVE member of. 404 otherwise
     * (never 403: do not reveal the resource exists).
     */
    private Category findAccessible(Long userId, Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        if (category.getSpaceId() == null) {
            if (!category.getUserId().equals(userId)) {
                throw new NotFoundException("Category not found");
            }
        } else {
            spaceAccess.assertActiveMember(userId, category.getSpaceId());
        }
        return category;
    }
}
