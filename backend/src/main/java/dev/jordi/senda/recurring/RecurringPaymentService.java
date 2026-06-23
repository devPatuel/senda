package dev.jordi.senda.recurring;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RecurringPaymentService {

    private static final BigDecimal TWELVE = new BigDecimal("12");

    private final RecurringPaymentRepository repository;
    private final CategoryRepository categoryRepository;

    public RecurringPaymentService(RecurringPaymentRepository repository,
                                   CategoryRepository categoryRepository) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<RecurringPaymentResponse> list(Long userId) {
        Map<Long, Category> categories = categoryRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        LocalDate today = LocalDate.now();
        return repository.findByUserId(userId).stream()
                .map(p -> toResponse(p, categories.get(p.getCategoryId()), today))
                .sorted(Comparator.comparing(RecurringPaymentResponse::nextDueDate))
                .toList();
    }

    @Transactional
    public RecurringPaymentResponse create(Long userId, RecurringPaymentRequest request) {
        Integer month = validateAndResolveMonth(userId, request);
        RecurringPayment saved = repository.save(new RecurringPayment(
                userId, request.name().trim(), request.amount(), request.frequency(),
                request.categoryId(), request.dayOfMonth(), month));
        Category category = categoryRepository.findByIdAndUserId(saved.getCategoryId(), userId).orElse(null);
        return toResponse(saved, category, LocalDate.now());
    }

    @Transactional
    public RecurringPaymentResponse update(Long userId, Long id, RecurringPaymentRequest request) {
        RecurringPayment payment = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Recurring payment not found"));
        Integer month = validateAndResolveMonth(userId, request);
        payment.setName(request.name().trim());
        payment.setAmount(request.amount());
        payment.setFrequency(request.frequency());
        payment.setCategoryId(request.categoryId());
        payment.setDayOfMonth(request.dayOfMonth());
        payment.setMonth(month);
        Category category = categoryRepository.findByIdAndUserId(payment.getCategoryId(), userId).orElse(null);
        // Managed entity: dirty checking flushes on commit
        return toResponse(payment, category, LocalDate.now());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        RecurringPayment payment = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Recurring payment not found"));
        repository.delete(payment);
    }

    // -------------------------------------------------------------------------
    // Validation + derivation
    // -------------------------------------------------------------------------

    /**
     * Validates the category (owned + expense) and the month rule, returning the
     * month to persist (null for MONTHLY, the request month for ANNUAL).
     */
    private Integer validateAndResolveMonth(Long userId, RecurringPaymentRequest request) {
        Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        if (category.getType() != TransactionType.EXPENSE) {
            throw new InvalidRecurringException("A recurring payment must use an expense category");
        }
        if (request.frequency() == RecurringFrequency.ANNUAL) {
            if (request.month() == null) {
                throw new InvalidRecurringException("month is required for annual payments");
            }
            return request.month();
        }
        return null;   // MONTHLY ignores month
    }

    private static RecurringPaymentResponse toResponse(RecurringPayment p, Category category, LocalDate today) {
        BigDecimal monthlyEquivalent = p.getFrequency() == RecurringFrequency.ANNUAL
                ? p.getAmount().divide(TWELVE, 2, RoundingMode.HALF_UP)
                : p.getAmount().setScale(2, RoundingMode.HALF_UP);
        return new RecurringPaymentResponse(
                p.getId(),
                p.getName(),
                p.getAmount(),
                p.getFrequency(),
                p.getCategoryId(),
                category != null ? category.getName() : null,
                category != null ? category.getColor() : null,
                p.getDayOfMonth(),
                p.getMonth(),
                nextDueDate(p.getFrequency(), p.getDayOfMonth(), p.getMonth(), today),
                monthlyEquivalent);
    }

    /**
     * Next occurrence of the configured day (and month, for annual) on or after
     * {@code today}. A day that does not exist in the target month is clamped to
     * the last day of that month (e.g. the 31st in February).
     */
    static LocalDate nextDueDate(RecurringFrequency frequency, int dayOfMonth, Integer month, LocalDate today) {
        if (frequency == RecurringFrequency.MONTHLY) {
            LocalDate candidate = atDay(today.getYear(), today.getMonthValue(), dayOfMonth);
            if (!candidate.isBefore(today)) {
                return candidate;
            }
            LocalDate nextMonth = today.plusMonths(1);
            return atDay(nextMonth.getYear(), nextMonth.getMonthValue(), dayOfMonth);
        }
        // ANNUAL: month is guaranteed non-null by validation
        LocalDate candidate = atDay(today.getYear(), month, dayOfMonth);
        if (!candidate.isBefore(today)) {
            return candidate;
        }
        return atDay(today.getYear() + 1, month, dayOfMonth);
    }

    private static LocalDate atDay(int year, int month, int dayOfMonth) {
        int lastDay = YearMonth.of(year, month).lengthOfMonth();
        return LocalDate.of(year, month, Math.min(dayOfMonth, lastDay));
    }
}
