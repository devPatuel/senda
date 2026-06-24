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
    private static final BigDecimal WEEKS_PER_YEAR = new BigDecimal("52");
    private static final BigDecimal THREE = new BigDecimal("3");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RecurringPaymentRepository repository;
    private final CategoryRepository categoryRepository;
    private final RecurringAmountHistoryRepository historyRepository;

    public RecurringPaymentService(RecurringPaymentRepository repository,
                                   CategoryRepository categoryRepository,
                                   RecurringAmountHistoryRepository historyRepository) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public List<RecurringPaymentResponse> list(Long userId) {
        Map<Long, Category> categories = categoryRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        List<RecurringPayment> payments = repository.findByUserId(userId);
        List<Long> ids = payments.stream().map(RecurringPayment::getId).toList();
        Map<Long, List<RecurringAmountHistory>> historyByPayment = ids.isEmpty()
                ? Map.of()
                : historyRepository.findByRecurringIdInOrderByChangedAtAsc(ids).stream()
                        .collect(Collectors.groupingBy(RecurringAmountHistory::getRecurringId));
        LocalDate today = LocalDate.now();
        return payments.stream()
                .map(p -> toResponse(p, categories.get(p.getCategoryId()), today,
                        previousAmountOf(historyByPayment.getOrDefault(p.getId(), List.of()))))
                .sorted(Comparator.comparing(RecurringPaymentResponse::nextDueDate))
                .toList();
    }

    @Transactional
    public RecurringPaymentResponse create(Long userId, RecurringPaymentRequest request) {
        validate(userId, request);
        RecurringPayment saved = repository.save(new RecurringPayment(
                userId, request.name().trim(), request.amount(), request.frequency(),
                request.categoryId(), request.dayOfMonth(), resolveMonth(request),
                resolveDayOfWeek(request), request.endDate()));
        // Baseline amount, so a later change can be compared against it
        historyRepository.save(new RecurringAmountHistory(saved.getId(), saved.getAmount()));
        Category category = categoryRepository.findByIdAndUserId(saved.getCategoryId(), userId).orElse(null);
        return toResponse(saved, category, LocalDate.now(), null);
    }

    @Transactional
    public RecurringPaymentResponse update(Long userId, Long id, RecurringPaymentRequest request) {
        RecurringPayment payment = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Recurring payment not found"));
        validate(userId, request);
        BigDecimal oldAmount = payment.getAmount();
        payment.setName(request.name().trim());
        payment.setAmount(request.amount());
        payment.setFrequency(request.frequency());
        payment.setCategoryId(request.categoryId());
        payment.setDayOfMonth(request.dayOfMonth());
        payment.setMonth(resolveMonth(request));
        payment.setDayOfWeek(resolveDayOfWeek(request));
        payment.setEndDate(request.endDate());
        // Record a new history entry only when the amount actually changed
        if (request.amount().compareTo(oldAmount) != 0) {
            historyRepository.save(new RecurringAmountHistory(payment.getId(), request.amount()));
        }
        Category category = categoryRepository.findByIdAndUserId(payment.getCategoryId(), userId).orElse(null);
        BigDecimal previousAmount = previousAmountOf(
                historyRepository.findByRecurringIdOrderByChangedAtAsc(payment.getId()));
        // Managed entity: dirty checking flushes on commit
        return toResponse(payment, category, LocalDate.now(), previousAmount);
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
     * Validates the category (owned + expense) and the frequency-specific required
     * fields: WEEKLY needs a day of week, ANNUAL and QUARTERLY need an anchor month.
     */
    private void validate(Long userId, RecurringPaymentRequest request) {
        Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        if (category.getType() != TransactionType.EXPENSE) {
            throw new InvalidRecurringException("A recurring payment must use an expense category");
        }
        switch (request.frequency()) {
            case WEEKLY -> {
                if (request.dayOfWeek() == null) {
                    throw new InvalidRecurringException("day of week is required for weekly payments");
                }
            }
            case QUARTERLY -> {
                if (request.month() == null) {
                    throw new InvalidRecurringException("month is required for quarterly payments");
                }
            }
            case ANNUAL -> {
                if (request.month() == null) {
                    throw new InvalidRecurringException("month is required for annual payments");
                }
            }
            case MONTHLY -> {
                // No extra fields required
            }
        }
    }

    // The anchor month is persisted for ANNUAL and QUARTERLY; null otherwise.
    private static Integer resolveMonth(RecurringPaymentRequest request) {
        return request.frequency() == RecurringFrequency.ANNUAL
                || request.frequency() == RecurringFrequency.QUARTERLY
                ? request.month() : null;
    }

    // The day of week is persisted for WEEKLY only.
    private static Integer resolveDayOfWeek(RecurringPaymentRequest request) {
        return request.frequency() == RecurringFrequency.WEEKLY ? request.dayOfWeek() : null;
    }

    private static RecurringPaymentResponse toResponse(RecurringPayment p, Category category, LocalDate today,
                                                       BigDecimal previousAmount) {
        BigDecimal monthlyEquivalent = switch (p.getFrequency()) {
            case WEEKLY -> p.getAmount().multiply(WEEKS_PER_YEAR).divide(TWELVE, 2, RoundingMode.HALF_UP);
            case QUARTERLY -> p.getAmount().divide(THREE, 2, RoundingMode.HALF_UP);
            case ANNUAL -> p.getAmount().divide(TWELVE, 2, RoundingMode.HALF_UP);
            case MONTHLY -> p.getAmount().setScale(2, RoundingMode.HALF_UP);
        };
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
                p.getDayOfWeek(),
                nextDueDate(p.getFrequency(), p.getDayOfMonth(), p.getMonth(), p.getDayOfWeek(), today),
                monthlyEquivalent,
                p.getEndDate(),
                previousAmount,
                changePct(previousAmount, p.getAmount()));
    }

    /** The amount before the most recent change (second-to-last entry), or null. */
    private static BigDecimal previousAmountOf(List<RecurringAmountHistory> historyAsc) {
        return historyAsc.size() >= 2 ? historyAsc.get(historyAsc.size() - 2).getAmount() : null;
    }

    /** Percentage change from {@code previous} to {@code current}, one decimal, or null. */
    private static BigDecimal changePct(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Next occurrence on or after {@code today}, by frequency:
     * <ul>
     *   <li>WEEKLY: the next date whose ISO day-of-week matches {@code dayOfWeek}
     *       ({@code today} itself counts when it already matches).</li>
     *   <li>MONTHLY: the configured day this month, else next month.</li>
     *   <li>QUARTERLY: the configured day in the next month whose distance from the
     *       anchor {@code month} is a multiple of 3.</li>
     *   <li>ANNUAL: the configured day/month this year, else next year.</li>
     * </ul>
     * A day that does not exist in the target month is clamped to its last day
     * (e.g. the 31st in February).
     */
    static LocalDate nextDueDate(RecurringFrequency frequency, int dayOfMonth, Integer month,
                                 Integer dayOfWeek, LocalDate today) {
        switch (frequency) {
            case WEEKLY -> {
                int delta = (dayOfWeek - today.getDayOfWeek().getValue() + 7) % 7;
                return today.plusDays(delta);
            }
            case MONTHLY -> {
                LocalDate candidate = atDay(today.getYear(), today.getMonthValue(), dayOfMonth);
                if (!candidate.isBefore(today)) {
                    return candidate;
                }
                LocalDate nextMonth = today.plusMonths(1);
                return atDay(nextMonth.getYear(), nextMonth.getMonthValue(), dayOfMonth);
            }
            case QUARTERLY -> {
                // Occurrences fall in every month m where (m - month) is a multiple
                // of 3. Step forward month by month and take the first clamped
                // candidate on or after today (within 13 steps a match always exists).
                YearMonth cursor = YearMonth.from(today);
                for (int i = 0; i < 13; i++) {
                    YearMonth ym = cursor.plusMonths(i);
                    if (((ym.getMonthValue() - month) % 3 + 3) % 3 == 0) {
                        LocalDate candidate = atDay(ym.getYear(), ym.getMonthValue(), dayOfMonth);
                        if (!candidate.isBefore(today)) {
                            return candidate;
                        }
                    }
                }
                throw new IllegalStateException("No quarterly occurrence found");
            }
            case ANNUAL -> {
                LocalDate candidate = atDay(today.getYear(), month, dayOfMonth);
                if (!candidate.isBefore(today)) {
                    return candidate;
                }
                return atDay(today.getYear() + 1, month, dayOfMonth);
            }
        }
        throw new IllegalStateException("Unhandled frequency: " + frequency);
    }

    private static LocalDate atDay(int year, int month, int dayOfMonth) {
        int lastDay = YearMonth.of(year, month).lengthOfMonth();
        return LocalDate.of(year, month, Math.min(dayOfMonth, lastDay));
    }
}
