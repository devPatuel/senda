package dev.jordi.senda.allocation;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryBalance;
import dev.jordi.senda.category.CategoryBalanceRepository;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryBalanceRepository balanceRepository;

    private AllocationService service;

    @BeforeEach
    void setUp() {
        service = new AllocationService(categoryRepository, balanceRepository);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Category expenseCategory(Long id, Long userId, String name, String pct) {
        Category c = new Category(userId, name, TransactionType.EXPENSE, "#10b981");
        ReflectionTestUtils.setField(c, "id", id);
        if (pct != null) {
            c.setTargetPercentage(new BigDecimal(pct));
        }
        return c;
    }

    private static CategoryBalance balance(Long categoryId, Long userId, String amount) {
        CategoryBalance b = new CategoryBalance(categoryId, userId);
        b.setBalance(new BigDecimal(amount));
        ReflectionTestUtils.setField(b, "id", categoryId * 10);
        return b;
    }

    private static EnvelopeLineRequest line(Long categoryId, String pct) {
        return new EnvelopeLineRequest(categoryId, new BigDecimal(pct));
    }

    // -------------------------------------------------------------------------
    // savePlan — validation
    // -------------------------------------------------------------------------

    @Test
    void savePlanThrowsConflictWhenPercentagesDontSumTo100() {
        EnvelopePlanRequest request = new EnvelopePlanRequest(List.of(
                line(1L, "50"),
                line(2L, "30")));  // sum = 80, not 100

        assertThatThrownBy(() -> service.savePlan(USER_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("100");

        verify(balanceRepository, never()).save(any());
    }

    @Test
    void savePlanAcceptsExactly100AndSetsTargets() {
        Category c1 = expenseCategory(1L, USER_ID, "Ahorro", null);
        Category c2 = expenseCategory(2L, USER_ID, "Inversión", null);
        Category c3 = expenseCategory(3L, USER_ID, "Ocio", null);

        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(c1, c2, c3));
        when(balanceRepository.findByCategoryId(any())).thenReturn(Optional.empty());
        when(balanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // list() after the mutation
        when(categoryRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(c1, c2, c3));
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(List.of());

        EnvelopePlanRequest request = new EnvelopePlanRequest(List.of(
                line(1L, "50"), line(2L, "20"), line(3L, "30")));

        List<EnvelopeResponse> result = service.savePlan(USER_ID, request);

        assertThat(result).extracting(EnvelopeResponse::name)
                .containsExactly("Ahorro", "Inversión", "Ocio");
        assertThat(c1.getTargetPercentage()).isEqualByComparingTo("50");
        assertThat(c2.getTargetPercentage()).isEqualByComparingTo("20");
        assertThat(c3.getTargetPercentage()).isEqualByComparingTo("30");
    }

    @Test
    void savePlanClearsTargetOnCategoriesNotInPlan() {
        Category inPlan = expenseCategory(1L, USER_ID, "Ahorro", null);
        Category dropped = expenseCategory(2L, USER_ID, "Viejo", "40");  // had a target

        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(inPlan, dropped));
        when(balanceRepository.findByCategoryId(any())).thenReturn(Optional.empty());
        when(balanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(inPlan, dropped));
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(List.of());

        service.savePlan(USER_ID, new EnvelopePlanRequest(List.of(line(1L, "100"))));

        assertThat(inPlan.getTargetPercentage()).isEqualByComparingTo("100");
        assertThat(dropped.getTargetPercentage()).isNull();
    }

    @Test
    void savePlanReturns404WhenCategoryBelongsToAnotherUser() {
        // No categories for USER_ID, but request references id=99
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of());

        EnvelopePlanRequest request = new EnvelopePlanRequest(List.of(line(99L, "100")));

        assertThatThrownBy(() -> service.savePlan(USER_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void savePlanRejectsForeignIdBeforeAnyMutation() {
        // The user owns category 5 and sneaks a foreign id (99) into the plan.
        // Authorization must fail BEFORE any target is changed.
        Category own = expenseCategory(5L, USER_ID, "Mío", null);
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(own));

        EnvelopePlanRequest request = new EnvelopePlanRequest(List.of(
                line(5L, "50"), line(99L, "50")));

        assertThatThrownBy(() -> service.savePlan(USER_ID, request))
                .isInstanceOf(NotFoundException.class);

        assertThat(own.getTargetPercentage()).isNull();   // not mutated
        verify(balanceRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // distribute — rounding and cent adjustment
    // -------------------------------------------------------------------------

    @Test
    void distributeReturnsCorrectSplitAndAdjustsCentsOnLastCategory() {
        // 33.33 + 33.33 + 33.34 = 100.00 exactly (classic rounding problem)
        Category c1 = expenseCategory(1L, USER_ID, "A", "33.33");
        Category c2 = expenseCategory(2L, USER_ID, "B", "33.33");
        Category c3 = expenseCategory(3L, USER_ID, "C", "33.34");

        when(categoryRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(c1, c2, c3));
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(List.of(
                balance(1L, USER_ID, "0.00"),
                balance(2L, USER_ID, "0.00"),
                balance(3L, USER_ID, "0.00")));

        DistributionResponse resp = service.distribute(USER_ID,
                new DistributeRequest(new BigDecimal("100.00"), false));

        List<DistributionLine> lines = resp.lines();
        BigDecimal total = lines.stream()
                .map(DistributionLine::allocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(total).isEqualByComparingTo("100.00");
        assertThat(lines.get(0).allocated()).isEqualByComparingTo("33.33");
        assertThat(lines.get(1).allocated()).isEqualByComparingTo("33.33");
        assertThat(lines.get(2).allocated()).isEqualByComparingTo("33.34");
    }

    @Test
    void distributeWithPersistAccumulatesBalances() {
        Category c1 = expenseCategory(1L, USER_ID, "Ahorro", "60");
        Category c2 = expenseCategory(2L, USER_ID, "Ocio", "40");

        CategoryBalance b1 = balance(1L, USER_ID, "100.00");
        CategoryBalance b2 = balance(2L, USER_ID, "50.00");

        when(categoryRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(c1, c2));
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(List.of(b1, b2));
        lenient().when(balanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.distribute(USER_ID, new DistributeRequest(new BigDecimal("1000.00"), true));

        assertThat(b1.getBalance()).isEqualByComparingTo("700.00");   // 100 + 600
        assertThat(b2.getBalance()).isEqualByComparingTo("450.00");   // 50 + 400
    }

    @Test
    void distributeThrowsWhenNoPlanDefined() {
        // Categories exist but none has a target percentage → no plan
        Category c1 = expenseCategory(1L, USER_ID, "Ahorro", null);
        when(categoryRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(c1));

        assertThatThrownBy(() -> service.distribute(USER_ID,
                new DistributeRequest(new BigDecimal("1000.00"), false)))
                .isInstanceOf(InvalidAllocationException.class);
    }

    @Test
    void distributeThrowsConflictWhenPlanDoesNotSumTo100() {
        // A single category with 50% is a corrupted plan
        Category c1 = expenseCategory(1L, USER_ID, "Ahorro", "50");
        when(categoryRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(c1));

        assertThatThrownBy(() -> service.distribute(USER_ID,
                new DistributeRequest(new BigDecimal("1000.00"), false)))
                .isInstanceOf(ConflictException.class);
    }
}
