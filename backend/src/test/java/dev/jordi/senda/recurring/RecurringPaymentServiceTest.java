package dev.jordi.senda.recurring;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringPaymentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CATEGORY_ID = 7L;

    @Mock
    private RecurringPaymentRepository repository;

    @Mock
    private CategoryRepository categoryRepository;

    private RecurringPaymentService service;

    @BeforeEach
    void setUp() {
        service = new RecurringPaymentService(repository, categoryRepository);
    }

    private static Category expenseCategory() {
        Category c = new Category(USER_ID, "Seguros", TransactionType.EXPENSE, "#3b82f6");
        ReflectionTestUtils.setField(c, "id", CATEGORY_ID);
        return c;
    }

    private static Category incomeCategory() {
        Category c = new Category(USER_ID, "Nómina", TransactionType.INCOME, "#22c55e");
        ReflectionTestUtils.setField(c, "id", CATEGORY_ID);
        return c;
    }

    // Convenience builder: most tests do not care about every field.
    private static RecurringPaymentRequest request(RecurringFrequency frequency, int dayOfMonth,
                                                   Integer month, Integer dayOfWeek, LocalDate endDate) {
        return new RecurringPaymentRequest("Pago", new BigDecimal("12.99"), frequency,
                CATEGORY_ID, dayOfMonth, month, dayOfWeek, endDate);
    }

    // -------------------------------------------------------------------------
    // nextDueDate — WEEKLY
    // -------------------------------------------------------------------------

    @Test
    void weeklyDueLaterThisWeek() {
        LocalDate today = LocalDate.of(2026, 6, 15);   // Monday (ISO 1)
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.WEEKLY, 1, null, 3, today))
                .isEqualTo(LocalDate.of(2026, 6, 17));  // Wednesday
    }

    @Test
    void weeklyDueTodayCountsAsToday() {
        LocalDate today = LocalDate.of(2026, 6, 15);   // Monday
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.WEEKLY, 1, null, 1, today))
                .isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void weeklyWrapsToNextWeek() {
        LocalDate today = LocalDate.of(2026, 6, 17);   // Wednesday (ISO 3)
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.WEEKLY, 1, null, 1, today))
                .isEqualTo(LocalDate.of(2026, 6, 22));  // next Monday
    }

    // -------------------------------------------------------------------------
    // nextDueDate — MONTHLY
    // -------------------------------------------------------------------------

    @Test
    void monthlyDueLaterThisMonth() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.MONTHLY, 20, null, null, today))
                .isEqualTo(LocalDate.of(2026, 6, 20));
    }

    @Test
    void monthlyDueTodayCountsAsToday() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.MONTHLY, 15, null, null, today))
                .isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void monthlyDayAlreadyPassedRollsToNextMonth() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.MONTHLY, 5, null, null, today))
                .isEqualTo(LocalDate.of(2026, 7, 5));
    }

    @Test
    void monthlyDay31ClampsToShorterMonth() {
        // June has 30 days, so the 31st clamps to the 30th (still >= today)
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.MONTHLY, 31, null, null, today))
                .isEqualTo(LocalDate.of(2026, 6, 30));
    }

    // -------------------------------------------------------------------------
    // nextDueDate — QUARTERLY
    // -------------------------------------------------------------------------

    @Test
    void quarterlyDueLaterThisQuarter() {
        // Anchor August (8); matching months are 8,11,2,5. From mid-June the next is Aug.
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.QUARTERLY, 10, 8, null, today))
                .isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void quarterlyAnchorThisMonthCountsWhenDayNotPassed() {
        // Anchor March (3); matching months 3,6,9,12. June 20 >= June 15.
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.QUARTERLY, 20, 3, null, today))
                .isEqualTo(LocalDate.of(2026, 6, 20));
    }

    @Test
    void quarterlyDayPassedRollsToNextMatchingMonth() {
        // Anchor March (3); June 20 already passed, so next match is September.
        LocalDate today = LocalDate.of(2026, 6, 25);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.QUARTERLY, 20, 3, null, today))
                .isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void quarterlyClampsFebruary() {
        // Anchor February (2); the 31st clamps to Feb 28 in 2026 (non-leap).
        LocalDate today = LocalDate.of(2026, 1, 1);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.QUARTERLY, 31, 2, null, today))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    // -------------------------------------------------------------------------
    // nextDueDate — ANNUAL
    // -------------------------------------------------------------------------

    @Test
    void annualDueLaterThisYear() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.ANNUAL, 25, 12, null, today))
                .isEqualTo(LocalDate.of(2026, 12, 25));
    }

    @Test
    void annualDateAlreadyPassedRollsToNextYear() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.ANNUAL, 1, 1, null, today))
                .isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void annualFeb29ClampsInNonLeapYear() {
        LocalDate today = LocalDate.of(2026, 1, 1);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.ANNUAL, 29, 2, null, today))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    // -------------------------------------------------------------------------
    // create — validation + derivation
    // -------------------------------------------------------------------------

    @Test
    void createAnnualWithoutMonthIsRejected() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(expenseCategory()));

        assertThatThrownBy(() -> service.create(USER_ID, request(RecurringFrequency.ANNUAL, 10, null, null, null)))
                .isInstanceOf(InvalidRecurringException.class)
                .hasMessageContaining("month");
    }

    @Test
    void createQuarterlyWithoutMonthIsRejected() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(expenseCategory()));

        assertThatThrownBy(() -> service.create(USER_ID, request(RecurringFrequency.QUARTERLY, 10, null, null, null)))
                .isInstanceOf(InvalidRecurringException.class)
                .hasMessageContaining("month");
    }

    @Test
    void createWeeklyWithoutDayOfWeekIsRejected() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(expenseCategory()));

        assertThatThrownBy(() -> service.create(USER_ID, request(RecurringFrequency.WEEKLY, 1, null, null, null)))
                .isInstanceOf(InvalidRecurringException.class)
                .hasMessageContaining("day of week");
    }

    @Test
    void createWithForeignCategoryReturns404() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID, request(RecurringFrequency.MONTHLY, 1, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createWithIncomeCategoryIsRejected() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(incomeCategory()));

        assertThatThrownBy(() -> service.create(USER_ID, request(RecurringFrequency.MONTHLY, 1, null, null, null)))
                .isInstanceOf(InvalidRecurringException.class);
    }

    @Test
    void createMonthlyComputesMonthlyEquivalentAndIgnoresMonth() {
        stubSaveWithId(1L);

        // month is provided but must be ignored for MONTHLY
        RecurringPaymentRequest req = new RecurringPaymentRequest(
                "Netflix", new BigDecimal("12.99"), RecurringFrequency.MONTHLY, CATEGORY_ID, 1, 5, null, null);

        RecurringPaymentResponse resp = service.create(USER_ID, req);

        assertThat(resp.month()).isNull();
        assertThat(resp.dayOfWeek()).isNull();
        assertThat(resp.monthlyEquivalent()).isEqualByComparingTo("12.99");
        assertThat(resp.categoryName()).isEqualTo("Seguros");
    }

    @Test
    void createAnnualComputesMonthlyEquivalentAsAmountOver12() {
        stubSaveWithId(2L);

        RecurringPaymentRequest req = new RecurringPaymentRequest(
                "Seguro coche", new BigDecimal("600.00"), RecurringFrequency.ANNUAL, CATEGORY_ID, 10, 3, null, null);

        RecurringPaymentResponse resp = service.create(USER_ID, req);

        assertThat(resp.month()).isEqualTo(3);
        assertThat(resp.monthlyEquivalent()).isEqualByComparingTo("50.00");  // 600 / 12
    }

    @Test
    void createWeeklyPersistsDayOfWeekAndComputesMonthlyEquivalent() {
        stubSaveWithId(3L);

        RecurringPaymentRequest req = new RecurringPaymentRequest(
                "Limpieza", new BigDecimal("10.00"), RecurringFrequency.WEEKLY, CATEGORY_ID, 1, null, 3, null);

        RecurringPaymentResponse resp = service.create(USER_ID, req);

        assertThat(resp.dayOfWeek()).isEqualTo(3);
        assertThat(resp.month()).isNull();
        assertThat(resp.monthlyEquivalent()).isEqualByComparingTo("43.33");  // 10 * 52 / 12
    }

    @Test
    void createQuarterlyComputesMonthlyEquivalentAsAmountOver3() {
        stubSaveWithId(4L);

        RecurringPaymentRequest req = new RecurringPaymentRequest(
                "Cuota", new BigDecimal("30.00"), RecurringFrequency.QUARTERLY, CATEGORY_ID, 10, 2, null, null);

        RecurringPaymentResponse resp = service.create(USER_ID, req);

        assertThat(resp.month()).isEqualTo(2);
        assertThat(resp.dayOfWeek()).isNull();
        assertThat(resp.monthlyEquivalent()).isEqualByComparingTo("10.00");  // 30 / 3
    }

    @Test
    void createPersistsEndDate() {
        stubSaveWithId(5L);

        LocalDate endDate = LocalDate.of(2026, 12, 31);
        RecurringPaymentRequest req = new RecurringPaymentRequest(
                "Netflix", new BigDecimal("12.99"), RecurringFrequency.MONTHLY, CATEGORY_ID, 1, null, null, endDate);

        RecurringPaymentResponse resp = service.create(USER_ID, req);

        assertThat(resp.endDate()).isEqualTo(endDate);
    }

    @Test
    void listSortsByNextDueDate() {
        // Two payments; the one due sooner must come first regardless of insert order
        RecurringPayment later = new RecurringPayment(
                USER_ID, "Anual", new BigDecimal("600.00"), RecurringFrequency.ANNUAL, CATEGORY_ID, 31, 12, null, null);
        ReflectionTestUtils.setField(later, "id", 1L);
        RecurringPayment sooner = new RecurringPayment(
                USER_ID, "Mensual", new BigDecimal("10.00"), RecurringFrequency.MONTHLY, CATEGORY_ID, 1, null, null, null);
        ReflectionTestUtils.setField(sooner, "id", 2L);

        when(repository.findByUserId(USER_ID)).thenReturn(java.util.List.of(later, sooner));
        lenient().when(categoryRepository.findByUserId(USER_ID))
                .thenReturn(java.util.List.of(expenseCategory()));

        var result = service.list(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).nextDueDate()).isBeforeOrEqualTo(result.get(1).nextDueDate());
    }

    private void stubSaveWithId(long id) {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(expenseCategory()));
        when(repository.save(any(RecurringPayment.class))).thenAnswer(inv -> {
            RecurringPayment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", id);
            return p;
        });
    }
}
