package dev.jordi.senda.transaction;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import dev.jordi.senda.space.SpaceAccess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_TREND_MONTHS = 24;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    // The user's local zone, so "current month" matches their transaction dates.
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final SpaceAccess spaceAccess;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              SpaceAccess spaceAccess) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.spaceAccess = spaceAccess;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(Long userId, Long spaceId, int page, int size,
                                                  LocalDate from, LocalDate to,
                                                  Long categoryId, TransactionType type) {
        if (page < 0) {
            throw new InvalidTransactionException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidTransactionException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (spaceId != null) {
            spaceAccess.assertActiveMember(userId, spaceId);
        }
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));
        Page<Transaction> result = transactionRepository.findAll(
                buildSpecification(userId, spaceId, from, to, categoryId, type), pageable);
        List<TransactionResponse> content = result.getContent().stream()
                .map(TransactionResponse::from)
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest request) {
        // On create the caller chooses the scope (personal or a space they belong to).
        Category category = resolveCategory(userId, request, request.spaceId(), null);
        Transaction transaction = new Transaction(userId, category, request.type(),
                request.amount(), request.date(), request.description());
        transaction.setSpaceId(request.spaceId());
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    /**
     * Quick capture (Apple Shortcut): always a personal EXPENSE dated today. When
     * no category is given, it is resolved from the user's category rules against
     * the description; if none match, a 400 asks for an explicit category or rule.
     */
    @Transactional
    public TransactionResponse quickCreate(Long userId, QuickTransactionRequest request) {
        Category category = resolveQuickCategory(userId, request);
        Transaction transaction = new Transaction(userId, category, TransactionType.EXPENSE,
                request.amount(), LocalDate.now(ZONE), request.description());
        // Personal scope: spaceId stays null.
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    private Category resolveQuickCategory(Long userId, QuickTransactionRequest request) {
        return categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(request.categoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long userId, Long id) {
        return TransactionResponse.from(findAccessible(userId, id));
    }

    @Transactional
    public TransactionResponse update(Long userId, Long id, TransactionRequest request) {
        Transaction transaction = findAccessible(userId, id);
        // The scope of an existing transaction is immutable: an edit resolves the
        // category within the transaction's own scope, never the request's. This
        // prevents re-scoping a shared movement into someone's personal ledger
        // (or vice versa) via the update body.
        Category category = resolveCategory(userId, request, transaction.getSpaceId(),
                transaction.getCategory().getId());
        transaction.setCategory(category);
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setDate(request.date());
        transaction.setDescription(request.description());
        // Managed entity: JPA dirty checking flushes the update on commit
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        transactionRepository.delete(findAccessible(userId, id));
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse summary(Long userId, Long spaceId, int year, int month) {
        if (year < 1 || year > 9999) {
            throw new InvalidTransactionException("year must be between 1 and 9999");
        }
        if (month < 1 || month > 12) {
            throw new InvalidTransactionException("month must be between 1 and 12");
        }
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.plusMonths(1).minusDays(1);
        List<CategorySummary> byCategory;
        if (spaceId == null) {
            byCategory = transactionRepository.summarizeByCategory(userId, from, to);
        } else {
            spaceAccess.assertActiveMember(userId, spaceId);
            byCategory = transactionRepository.summarizeByCategoryForSpace(spaceId, from, to);
        }
        // Transfers (own money moved between accounts) are reported apart: counting
        // them as income or spending would inflate both totals and every ratio
        // derived from them.
        List<CategorySummary> real = byCategory.stream()
                .filter(summary -> !summary.transfer())
                .toList();
        BigDecimal totalIncome = sumByType(real, TransactionType.INCOME);
        BigDecimal totalExpense = sumByType(real, TransactionType.EXPENSE);
        List<CategorySummary> transfers = byCategory.stream()
                .filter(CategorySummary::transfer)
                .toList();
        BigDecimal transfersIn = sumByType(transfers, TransactionType.INCOME);
        BigDecimal transfersOut = sumByType(transfers, TransactionType.EXPENSE);

        List<CategorySummary> expenses = real.stream()
                .filter(summary -> summary.type() == TransactionType.EXPENSE)
                .toList();
        BigDecimal fixedExpenseTotal = expenses.stream()
                .filter(CategorySummary::fixed)
                .map(CategorySummary::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal variableExpenseTotal = totalExpense.subtract(fixedExpenseTotal);
        // Null (not zero) when there is no income: a ratio "of nothing" is
        // meaningless, and the frontend must tell "0%" apart from "not applicable".
        BigDecimal fixedExpensePercentage = totalIncome.signum() == 0 ? null
                : fixedExpenseTotal.multiply(BigDecimal.valueOf(100))
                        .divide(totalIncome, 2, RoundingMode.HALF_UP);
        CategorySummary topExpenseCategory = expenses.stream()
                .max(Comparator.comparing(CategorySummary::total))
                .orElse(null);

        return new MonthlySummaryResponse(year, month, totalIncome, totalExpense,
                totalIncome.subtract(totalExpense), byCategory,
                fixedExpenseTotal, variableExpenseTotal, fixedExpensePercentage, topExpenseCategory,
                transfersIn, transfersOut);
    }

    /**
     * A calendar year aggregated: what was spent, what came in as transfers and
     * the twelve months in order.
     */
    @Transactional(readOnly = true)
    public YearSummaryResponse yearSummary(Long userId, Long spaceId, int year) {
        if (year < 1 || year > 9999) {
            throw new InvalidTransactionException("year must be between 1 and 9999");
        }
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<MonthlyTotal> monthlyTotals;
        if (spaceId == null) {
            monthlyTotals = transactionRepository.monthlyTotalsBetween(userId, from, to);
        } else {
            spaceAccess.assertActiveMember(userId, spaceId);
            monthlyTotals = transactionRepository.monthlyTotalsBetweenForSpace(spaceId, from, to);
        }
        List<MonthlyTotal> transferTotals =
                transactionRepository.monthlyTransfersIn(userId, spaceId, from, to);

        Map<Integer, BigDecimal> expenseByMonth = monthlyTotals.stream()
                .filter(t -> t.type() == TransactionType.EXPENSE)
                .collect(Collectors.toMap(MonthlyTotal::month, MonthlyTotal::total));
        Map<Integer, BigDecimal> transfersByMonth = transferTotals.stream()
                .collect(Collectors.toMap(MonthlyTotal::month, MonthlyTotal::total));

        List<YearMonthTotals> months = new ArrayList<>(12);
        BigDecimal totalExpense = ZERO;
        BigDecimal totalTransfersIn = ZERO;
        for (int m = 1; m <= 12; m++) {
            BigDecimal expense = expenseByMonth.getOrDefault(m, ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal transfersIn = transfersByMonth.getOrDefault(m, ZERO).setScale(2, RoundingMode.HALF_UP);
            totalExpense = totalExpense.add(expense);
            totalTransfersIn = totalTransfersIn.add(transfersIn);
            months.add(new YearMonthTotals(year, m, expense, transfersIn));
        }

        return new YearSummaryResponse(year, totalExpense, totalTransfersIn, months);
    }

    /**
     * Dense income/expense/balance series for the last {@code months} months
     * (current month included). Months with no transactions appear as zeros, so
     * the caller always gets exactly {@code months} ordered rows.
     */
    @Transactional(readOnly = true)
    public List<MonthlyTrend> trends(Long userId, int months) {
        if (months < 1 || months > MAX_TREND_MONTHS) {
            throw new InvalidTransactionException("months must be between 1 and " + MAX_TREND_MONTHS);
        }
        YearMonth current = YearMonth.now(ZONE);
        YearMonth start = current.minusMonths(months - 1L);
        LocalDate from = start.atDay(1);

        // Index the DB aggregates by (yearMonth, type) for O(1) lookup per bucket.
        Map<YearMonth, Map<TransactionType, BigDecimal>> byMonth = transactionRepository
                .monthlyTotals(userId, from).stream()
                .collect(Collectors.groupingBy(
                        t -> YearMonth.of(t.year(), t.month()),
                        Collectors.toMap(MonthlyTotal::type, MonthlyTotal::total)));

        List<MonthlyTrend> series = new ArrayList<>(months);
        for (int i = 0; i < months; i++) {
            YearMonth ym = start.plusMonths(i);
            Map<TransactionType, BigDecimal> totals = byMonth.getOrDefault(ym, Map.of());
            BigDecimal income = totals.getOrDefault(TransactionType.INCOME, ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal expense = totals.getOrDefault(TransactionType.EXPENSE, ZERO).setScale(2, RoundingMode.HALF_UP);
            series.add(new MonthlyTrend(ym.getYear(), ym.getMonthValue(), income, expense, income.subtract(expense)));
        }
        return series;
    }

    /**
     * Loads a transaction the caller may act on: a personal one they authored, or
     * a shared one of a space they are an ACTIVE member of. 404 otherwise (never
     * 403: do not reveal the resource exists). get/delete carry no spaceId, so the
     * scope is read off the row itself.
     */
    private Transaction findAccessible(Long userId, Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
        if (transaction.getSpaceId() == null) {
            if (!transaction.getUserId().equals(userId)) {
                throw new NotFoundException("Transaction not found");
            }
        } else {
            spaceAccess.assertActiveMember(userId, transaction.getSpaceId());
        }
        return transaction;
    }

    private Category resolveCategory(Long userId, TransactionRequest request, Long spaceId,
                                     Long currentCategoryId) {
        // A personal transaction must use a personal category; a space transaction a
        // category of that same space. Resolving in the given scope enforces the
        // rule: a cross-scope categoryId simply is not found -> 404. The scope comes
        // from the request on create and from the existing transaction on update.
        Category category;
        if (spaceId == null) {
            category = categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(request.categoryId(), userId)
                    .orElseThrow(() -> new NotFoundException("Category not found"));
        } else {
            spaceAccess.assertActiveMember(userId, spaceId);
            category = categoryRepository.findByIdAndSpaceId(request.categoryId(), spaceId)
                    .orElseThrow(() -> new NotFoundException("Category not found"));
        }
        // Keeping the transaction's current category is allowed even when it was
        // deactivated (soft-deleted): otherwise a transaction whose category was
        // deactivated could never be edited without also changing its category.
        boolean keepsCurrentCategory = category.getId().equals(currentCategoryId);
        if (!category.isActive() && !keepsCurrentCategory) {
            throw new ConflictException("Category is inactive");
        }
        if (category.getType() != request.type()) {
            throw new InvalidTransactionException("Transaction type " + request.type()
                    + " does not match category type " + category.getType());
        }
        return category;
    }

    private Specification<Transaction> buildSpecification(Long userId, Long spaceId, LocalDate from, LocalDate to,
                                                          Long categoryId, TransactionType type) {
        // Personal: scope to the user AND exclude space rows (they keep the author's
        // user_id, so without isNull(spaceId) they would leak into personal views).
        // Space: scope to the space; both members see every row regardless of author.
        Specification<Transaction> spec = spaceId == null
                ? (root, query, cb) -> cb.and(cb.equal(root.get("userId"), userId), cb.isNull(root.get("spaceId")))
                : (root, query, cb) -> cb.equal(root.get("spaceId"), spaceId);
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        return spec;
    }

    private static BigDecimal sumByType(List<CategorySummary> byCategory, TransactionType type) {
        return byCategory.stream()
                .filter(summary -> summary.type() == type)
                .map(CategorySummary::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                // Inputs are NUMERIC(12,2): scaling to 2 never rounds
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
