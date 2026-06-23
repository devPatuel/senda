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

    // -------------------------------------------------------------------------
    // nextDueDate — MONTHLY
    // -------------------------------------------------------------------------

    @Test
    void monthlyDueLaterThisMonth() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.MONTHLY, 20, null, today))
                .isEqualTo(LocalDate.of(2026, 6, 20));
    }

    @Test
    void monthlyDueTodayCountsAsToday() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.MONTHLY, 15, null, today))
                .isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void monthlyDayAlreadyPassedRollsToNextMonth() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.MONTHLY, 5, null, today))
                .isEqualTo(LocalDate.of(2026, 7, 5));
    }

    @Test
    void monthlyDay31ClampsToShorterMonth() {
        // June has 30 days, so the 31st clamps to the 30th (still >= today)
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.MONTHLY, 31, null, today))
                .isEqualTo(LocalDate.of(2026, 6, 30));
    }

    // -------------------------------------------------------------------------
    // nextDueDate — ANNUAL
    // -------------------------------------------------------------------------

    @Test
    void annualDueLaterThisYear() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.ANNUAL, 25, 12, today))
                .isEqualTo(LocalDate.of(2026, 12, 25));
    }

    @Test
    void annualDateAlreadyPassedRollsToNextYear() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.ANNUAL, 1, 1, today))
                .isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void annualFeb29ClampsInNonLeapYear() {
        LocalDate today = LocalDate.of(2026, 1, 1);
        assertThat(RecurringPaymentService.nextDueDate(RecurringFrequency.ANNUAL, 29, 2, today))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    // -------------------------------------------------------------------------
    // create — validation + derivation
    // -------------------------------------------------------------------------

    @Test
    void createAnnualWithoutMonthIsRejected() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(expenseCategory()));

        RecurringPaymentRequest req = new RecurringPaymentRequest(
                "Seguro coche", new BigDecimal("600.00"), RecurringFrequency.ANNUAL, CATEGORY_ID, 10, null);

        assertThatThrownBy(() -> service.create(USER_ID, req))
                .isInstanceOf(InvalidRecurringException.class)
                .hasMessageContaining("month");
    }

    @Test
    void createWithForeignCategoryReturns404() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        RecurringPaymentRequest req = new RecurringPaymentRequest(
                "Netflix", new BigDecimal("12.99"), RecurringFrequency.MONTHLY, CATEGORY_ID, 1, null);

        assertThatThrownBy(() -> service.create(USER_ID, req))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createWithIncomeCategoryIsRejected() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(incomeCategory()));

        RecurringPaymentRequest req = new RecurringPaymentRequest(
                "Netflix", new BigDecimal("12.99"), RecurringFrequency.MONTHLY, CATEGORY_ID, 1, null);

        assertThatThrownBy(() -> service.create(USER_ID, req))
                .isInstanceOf(InvalidRecurringException.class);
    }

    @Test
    void createMonthlyComputesMonthlyEquivalentAndIgnoresMonth() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(expenseCategory()));
        when(repository.save(any(RecurringPayment.class))).thenAnswer(inv -> {
            RecurringPayment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 1L);
            return p;
        });

        // month is provided but must be ignored for MONTHLY
        RecurringPaymentRequest req = new RecurringPaymentRequest(
                "Netflix", new BigDecimal("12.99"), RecurringFrequency.MONTHLY, CATEGORY_ID, 1, 5);

        RecurringPaymentResponse resp = service.create(USER_ID, req);

        assertThat(resp.month()).isNull();
        assertThat(resp.monthlyEquivalent()).isEqualByComparingTo("12.99");
        assertThat(resp.categoryName()).isEqualTo("Seguros");
    }

    @Test
    void createAnnualComputesMonthlyEquivalentAsAmountOver12() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(expenseCategory()));
        when(repository.save(any(RecurringPayment.class))).thenAnswer(inv -> {
            RecurringPayment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 2L);
            return p;
        });

        RecurringPaymentRequest req = new RecurringPaymentRequest(
                "Seguro coche", new BigDecimal("600.00"), RecurringFrequency.ANNUAL, CATEGORY_ID, 10, 3);

        RecurringPaymentResponse resp = service.create(USER_ID, req);

        assertThat(resp.month()).isEqualTo(3);
        assertThat(resp.monthlyEquivalent()).isEqualByComparingTo("50.00");  // 600 / 12
    }

    @Test
    void listSortsByNextDueDate() {
        // Two payments; the one due sooner must come first regardless of insert order
        RecurringPayment later = new RecurringPayment(
                USER_ID, "Anual", new BigDecimal("600.00"), RecurringFrequency.ANNUAL, CATEGORY_ID, 31, 12);
        ReflectionTestUtils.setField(later, "id", 1L);
        RecurringPayment sooner = new RecurringPayment(
                USER_ID, "Mensual", new BigDecimal("10.00"), RecurringFrequency.MONTHLY, CATEGORY_ID, 1, null);
        ReflectionTestUtils.setField(sooner, "id", 2L);

        when(repository.findByUserId(USER_ID)).thenReturn(java.util.List.of(later, sooner));
        lenient().when(categoryRepository.findByUserId(USER_ID))
                .thenReturn(java.util.List.of(expenseCategory()));

        var result = service.list(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).nextDueDate()).isBeforeOrEqualTo(result.get(1).nextDueDate());
    }
}
