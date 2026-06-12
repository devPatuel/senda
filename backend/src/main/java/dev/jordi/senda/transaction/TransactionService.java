package dev.jordi.senda.transaction;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
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
import java.util.List;

@Service
public class TransactionService {

    static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(Long userId, int page, int size,
                                                  LocalDate from, LocalDate to,
                                                  Long categoryId, TransactionType type) {
        if (page < 0) {
            throw new InvalidTransactionException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidTransactionException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));
        Page<Transaction> result = transactionRepository.findAll(
                buildSpecification(userId, from, to, categoryId, type), pageable);
        List<TransactionResponse> content = result.getContent().stream()
                .map(TransactionResponse::from)
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest request) {
        Category category = resolveCategory(userId, request);
        Transaction transaction = new Transaction(userId, category, request.type(),
                request.amount(), request.date(), request.description());
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long userId, Long id) {
        return TransactionResponse.from(findOwned(userId, id));
    }

    @Transactional
    public TransactionResponse update(Long userId, Long id, TransactionRequest request) {
        Transaction transaction = findOwned(userId, id);
        Category category = resolveCategory(userId, request);
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
        transactionRepository.delete(findOwned(userId, id));
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse summary(Long userId, int year, int month) {
        if (year < 1 || year > 9999) {
            throw new InvalidTransactionException("year must be between 1 and 9999");
        }
        if (month < 1 || month > 12) {
            throw new InvalidTransactionException("month must be between 1 and 12");
        }
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.plusMonths(1).minusDays(1);
        List<CategorySummary> byCategory = transactionRepository.summarizeByCategory(userId, from, to);
        BigDecimal totalIncome = sumByType(byCategory, TransactionType.INCOME);
        BigDecimal totalExpense = sumByType(byCategory, TransactionType.EXPENSE);
        return new MonthlySummaryResponse(year, month, totalIncome, totalExpense,
                totalIncome.subtract(totalExpense), byCategory);
    }

    private Transaction findOwned(Long userId, Long id) {
        // Foreign or missing resource both map to 404 to avoid leaking existence
        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
    }

    private Category resolveCategory(Long userId, TransactionRequest request) {
        Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        if (!category.isActive()) {
            throw new ConflictException("Category is inactive");
        }
        if (category.getType() != request.type()) {
            throw new InvalidTransactionException("Transaction type " + request.type()
                    + " does not match category type " + category.getType());
        }
        return category;
    }

    private Specification<Transaction> buildSpecification(Long userId, LocalDate from, LocalDate to,
                                                          Long categoryId, TransactionType type) {
        // userId always present: every query is scoped to the authenticated user
        Specification<Transaction> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);
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
