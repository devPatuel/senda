package dev.jordi.senda.transaction;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import dev.jordi.senda.space.SpaceAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SpaceAccess spaceAccess;

    private TransactionService service;

    private Category expenseCategory;
    private Category incomeCategory;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, categoryRepository, spaceAccess);
        expenseCategory = category(5L, "Comida", TransactionType.EXPENSE, true);
        incomeCategory = category(6L, "Nómina", TransactionType.INCOME, true);
    }

    private static Category category(Long id, String name, TransactionType type, boolean active) {
        Category category = new Category(USER_ID, name, type, "#EF4444");
        ReflectionTestUtils.setField(category, "id", id);
        category.setActive(active);
        return category;
    }

    private static Category spaceCategory(Long id, Long spaceId, TransactionType type) {
        Category category = new Category(USER_ID, "Cena fuera", type, "#EF4444");
        ReflectionTestUtils.setField(category, "id", id);
        category.setActive(true);
        category.setSpaceId(spaceId);
        return category;
    }

    private static TransactionRequest expenseRequest(Long categoryId) {
        return new TransactionRequest(categoryId, TransactionType.EXPENSE,
                new BigDecimal("12.50"), LocalDate.of(2026, 6, 10), "Lunch", null);
    }

    // --- create ---

    @Test
    void createSavesTransactionAndMapsResponse() {
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(5L, USER_ID)).thenReturn(Optional.of(expenseCategory));
        when(transactionRepository.save(org.mockito.ArgumentMatchers.any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 10L);
                    return saved;
                });

        TransactionResponse response = service.create(USER_ID, expenseRequest(5L));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.categoryId()).isEqualTo(5L);
        assertThat(response.categoryName()).isEqualTo("Comida");
        assertThat(response.categoryColor()).isEqualTo("#EF4444");
        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.amount()).isEqualByComparingTo("12.50");
        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(response.description()).isEqualTo("Lunch");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void createWithForeignOrMissingCategoryThrowsNotFound() {
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID, expenseRequest(5L)))
                .isInstanceOf(NotFoundException.class);
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createWithMismatchedTypeThrowsInvalidTransaction() {
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(6L, USER_ID)).thenReturn(Optional.of(incomeCategory));

        assertThatThrownBy(() -> service.create(USER_ID, expenseRequest(6L)))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("does not match category type");
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createWithInactiveCategoryThrowsConflict() {
        Category inactive = category(5L, "Comida", TransactionType.EXPENSE, false);
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(5L, USER_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.create(USER_ID, expenseRequest(5L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createInSpaceResolvesSpaceCategoryAndSetsSpaceId() {
        Category spaceCat = spaceCategory(3L, 7L, TransactionType.EXPENSE);
        when(categoryRepository.findByIdAndSpaceId(3L, 7L)).thenReturn(Optional.of(spaceCat));
        when(transactionRepository.save(org.mockito.ArgumentMatchers.any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(USER_ID, new TransactionRequest(3L, TransactionType.EXPENSE,
                new BigDecimal("20.00"), LocalDate.now(), "Cena", 7L));

        verify(spaceAccess).assertActiveMember(USER_ID, 7L);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getSpaceId()).isEqualTo(7L);
    }

    @Test
    void createPersonalRejectsSpaceCategory() {
        // A personal request (spaceId=null) can only see personal categories;
        // a space category is not found as personal -> 404.
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(3L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID, new TransactionRequest(3L, TransactionType.EXPENSE,
                new BigDecimal("20.00"), LocalDate.now(), "x", null)))
                .isInstanceOf(NotFoundException.class);
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // --- update ---

    @Test
    void updateChangesCategoryAndFields() {
        Transaction existing = new Transaction(USER_ID, expenseCategory, TransactionType.EXPENSE,
                new BigDecimal("12.50"), LocalDate.of(2026, 6, 10), "Lunch");
        ReflectionTestUtils.setField(existing, "id", 10L);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(6L, USER_ID)).thenReturn(Optional.of(incomeCategory));

        TransactionRequest request = new TransactionRequest(6L, TransactionType.INCOME,
                new BigDecimal("1500.00"), LocalDate.of(2026, 6, 1), null, null);
        TransactionResponse response = service.update(USER_ID, 10L, request);

        assertThat(response.categoryId()).isEqualTo(6L);
        assertThat(response.categoryName()).isEqualTo("Nómina");
        assertThat(response.type()).isEqualTo(TransactionType.INCOME);
        assertThat(response.amount()).isEqualByComparingTo("1500.00");
        assertThat(response.description()).isNull();
        assertThat(existing.getCategory()).isSameAs(incomeCategory);
    }

    @Test
    void updateCannotRescopeTransactionOutOfItsSpace() {
        // A shared transaction (space 7) must stay shared even if the update body
        // tries to personalize it (spaceId=null). The category is resolved in the
        // transaction's own scope, never the request's.
        Category spaceCat = spaceCategory(3L, 7L, TransactionType.EXPENSE);
        Transaction existing = new Transaction(USER_ID, spaceCat, TransactionType.EXPENSE,
                new BigDecimal("20.00"), LocalDate.of(2026, 6, 10), "Cena");
        ReflectionTestUtils.setField(existing, "id", 10L);
        ReflectionTestUtils.setField(existing, "spaceId", 7L);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByIdAndSpaceId(3L, 7L)).thenReturn(Optional.of(spaceCat));

        // Request tries to move it to personal scope (spaceId=null)
        TransactionRequest request = new TransactionRequest(3L, TransactionType.EXPENSE,
                new BigDecimal("25.00"), LocalDate.of(2026, 6, 11), "Cena", null);
        service.update(USER_ID, 10L, request);

        // Scope is immutable: still in space 7, resolved via the space query
        assertThat(existing.getSpaceId()).isEqualTo(7L);
        verify(categoryRepository).findByIdAndSpaceId(3L, 7L);
        verify(categoryRepository, never()).findByIdAndUserIdAndSpaceIdIsNull(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateKeepingInactiveCategorySucceeds() {
        // The category was soft-deleted after the transaction was created:
        // editing amount/date while keeping the category must still work.
        Category inactive = category(5L, "Comida", TransactionType.EXPENSE, false);
        Transaction existing = new Transaction(USER_ID, inactive, TransactionType.EXPENSE,
                new BigDecimal("12.50"), LocalDate.of(2026, 6, 10), "Lunch");
        ReflectionTestUtils.setField(existing, "id", 10L);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(5L, USER_ID)).thenReturn(Optional.of(inactive));

        TransactionRequest request = new TransactionRequest(5L, TransactionType.EXPENSE,
                new BigDecimal("20.00"), LocalDate.of(2026, 6, 11), "Lunch", null);
        TransactionResponse response = service.update(USER_ID, 10L, request);

        assertThat(response.categoryId()).isEqualTo(5L);
        assertThat(response.amount()).isEqualByComparingTo("20.00");
        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 6, 11));
    }

    @Test
    void updateChangingToInactiveCategoryThrowsConflict() {
        Transaction existing = new Transaction(USER_ID, expenseCategory, TransactionType.EXPENSE,
                new BigDecimal("12.50"), LocalDate.of(2026, 6, 10), "Lunch");
        ReflectionTestUtils.setField(existing, "id", 10L);
        Category otherInactive = category(7L, "Caprichos", TransactionType.EXPENSE, false);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(7L, USER_ID)).thenReturn(Optional.of(otherInactive));

        assertThatThrownBy(() -> service.update(USER_ID, 10L, expenseRequest(7L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateForeignOrMissingTransactionThrowsNotFound() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, 99L, expenseRequest(5L)))
                .isInstanceOf(NotFoundException.class);
    }

    // --- delete ---

    @Test
    void deleteRemovesOwnedTransaction() {
        Transaction existing = new Transaction(USER_ID, expenseCategory, TransactionType.EXPENSE,
                new BigDecimal("12.50"), LocalDate.of(2026, 6, 10), null);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(existing));

        service.delete(USER_ID, 10L);

        verify(transactionRepository).delete(existing);
    }

    @Test
    void deleteForeignOrMissingTransactionThrowsNotFound() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    // --- list ---

    @Test
    void listRejectsNegativePage() {
        assertThatThrownBy(() -> service.list(USER_ID, null, -1, 20, null, null, null, null))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void listRejectsSizeOutOfRange() {
        assertThatThrownBy(() -> service.list(USER_ID, null, 0, 101, null, null, null, null))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> service.list(USER_ID, null, 0, 0, null, null, null, null))
                .isInstanceOf(InvalidTransactionException.class);
    }

    // --- summary ---

    @Test
    void summaryRejectsInvalidMonth() {
        assertThatThrownBy(() -> service.summary(USER_ID, null, 2026, 0))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> service.summary(USER_ID, null, 2026, 13))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void summaryComputesTotalsAndBalanceFromAggregation() {
        List<CategorySummary> rows = List.of(
                new CategorySummary(6L, "Nómina", "#22C55E", TransactionType.INCOME, new BigDecimal("1500.00"), false),
                new CategorySummary(5L, "Comida", "#EF4444", TransactionType.EXPENSE, new BigDecimal("100.50"), false),
                new CategorySummary(7L, "Transporte", "#3B82F6", TransactionType.EXPENSE, new BigDecimal("20.25"), false));
        when(transactionRepository.summarizeByCategory(USER_ID,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))).thenReturn(rows);

        MonthlySummaryResponse summary = service.summary(USER_ID, null, 2026, 6);

        assertThat(summary.year()).isEqualTo(2026);
        assertThat(summary.month()).isEqualTo(6);
        assertThat(summary.totalIncome()).isEqualByComparingTo("1500.00");
        assertThat(summary.totalExpense()).isEqualByComparingTo("120.75");
        assertThat(summary.balance()).isEqualByComparingTo("1379.25");
        assertThat(summary.byCategory()).hasSize(3);
    }

    @Test
    void summaryWithNoTransactionsReturnsZeroTotals() {
        when(transactionRepository.summarizeByCategory(USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))).thenReturn(List.of());

        MonthlySummaryResponse summary = service.summary(USER_ID, null, 2026, 1);

        assertThat(summary.totalIncome()).isEqualByComparingTo("0.00");
        assertThat(summary.totalExpense()).isEqualByComparingTo("0.00");
        assertThat(summary.balance()).isEqualByComparingTo("0.00");
        assertThat(summary.byCategory()).isEmpty();
        assertThat(summary.fixedExpenseTotal()).isEqualByComparingTo("0.00");
        assertThat(summary.variableExpenseTotal()).isEqualByComparingTo("0.00");
        assertThat(summary.fixedExpensePercentage()).isNull();
        assertThat(summary.topExpenseCategory()).isNull();
    }

    @Test
    void summaryComputesFixedVariableTotalsAndTopExpenseCategory() {
        List<CategorySummary> rows = List.of(
                new CategorySummary(6L, "Nómina", "#22C55E", TransactionType.INCOME, new BigDecimal("2000.00"), false),
                new CategorySummary(8L, "Vivienda", "#8B5CF6", TransactionType.EXPENSE, new BigDecimal("800.00"), true),
                new CategorySummary(5L, "Comida", "#EF4444", TransactionType.EXPENSE, new BigDecimal("300.00"), false),
                new CategorySummary(7L, "Transporte", "#3B82F6", TransactionType.EXPENSE, new BigDecimal("50.00"), false));
        when(transactionRepository.summarizeByCategory(USER_ID,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))).thenReturn(rows);

        MonthlySummaryResponse summary = service.summary(USER_ID, null, 2026, 6);

        assertThat(summary.fixedExpenseTotal()).isEqualByComparingTo("800.00");
        assertThat(summary.variableExpenseTotal()).isEqualByComparingTo("350.00");
        assertThat(summary.fixedExpensePercentage()).isEqualByComparingTo("40.00");
        assertThat(summary.topExpenseCategory().categoryName()).isEqualTo("Vivienda");
    }

    @Test
    void summaryFixedExpensePercentageIsNullWhenNoIncome() {
        List<CategorySummary> rows = List.of(
                new CategorySummary(5L, "Comida", "#EF4444", TransactionType.EXPENSE, new BigDecimal("100.00"), false));
        when(transactionRepository.summarizeByCategory(USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))).thenReturn(rows);

        MonthlySummaryResponse summary = service.summary(USER_ID, null, 2026, 1);

        assertThat(summary.fixedExpensePercentage()).isNull();
        assertThat(summary.topExpenseCategory().categoryName()).isEqualTo("Comida");
    }

    // --- trends ---

    @Test
    void trendsRejectsOutOfRange() {
        assertThatThrownBy(() -> service.trends(USER_ID, 0))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> service.trends(USER_ID, 25))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void trendsReturnsDenseOrderedSeriesWithZeroFill() {
        // Same zone the service uses, so month boundaries never make this flaky.
        java.time.YearMonth cur = java.time.YearMonth.now(java.time.ZoneId.of("Europe/Madrid"));
        java.time.YearMonth twoAgo = cur.minusMonths(2);
        // Data only for two-months-ago (expense) and the current month (both types);
        // the month in between must be zero-filled.
        List<MonthlyTotal> rows = List.of(
                new MonthlyTotal(twoAgo.getYear(), twoAgo.getMonthValue(),
                        TransactionType.EXPENSE, new BigDecimal("50.00")),
                new MonthlyTotal(cur.getYear(), cur.getMonthValue(),
                        TransactionType.INCOME, new BigDecimal("1000.00")),
                new MonthlyTotal(cur.getYear(), cur.getMonthValue(),
                        TransactionType.EXPENSE, new BigDecimal("300.00")));
        when(transactionRepository.monthlyTotals(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.any())).thenReturn(rows);

        List<MonthlyTrend> series = service.trends(USER_ID, 3);

        assertThat(series).hasSize(3);
        // Oldest first
        assertThat(series.get(0).year()).isEqualTo(twoAgo.getYear());
        assertThat(series.get(0).month()).isEqualTo(twoAgo.getMonthValue());
        assertThat(series.get(0).income()).isEqualByComparingTo("0.00");
        assertThat(series.get(0).expense()).isEqualByComparingTo("50.00");
        assertThat(series.get(0).balance()).isEqualByComparingTo("-50.00");
        // Middle month has no data -> zeros
        assertThat(series.get(1).income()).isEqualByComparingTo("0.00");
        assertThat(series.get(1).expense()).isEqualByComparingTo("0.00");
        assertThat(series.get(1).balance()).isEqualByComparingTo("0.00");
        // Current month
        assertThat(series.get(2).year()).isEqualTo(cur.getYear());
        assertThat(series.get(2).month()).isEqualTo(cur.getMonthValue());
        assertThat(series.get(2).income()).isEqualByComparingTo("1000.00");
        assertThat(series.get(2).expense()).isEqualByComparingTo("300.00");
        assertThat(series.get(2).balance()).isEqualByComparingTo("700.00");
    }
}
