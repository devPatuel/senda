package dev.jordi.senda.category;

import dev.jordi.senda.account.AccountRepository;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import dev.jordi.senda.space.SpaceAccess;
import dev.jordi.senda.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryBalanceRepository categoryBalanceRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SpaceAccess spaceAccess;

    @InjectMocks
    private CategoryService categoryService;

    private static Category category(Long id, String name, TransactionType type, String color) {
        Category category = new Category(USER_ID, name, type, color);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    // --- list ---

    @Test
    void listReturnsActiveCategoriesSortedByName() {
        when(categoryRepository.findByUserIdAndSpaceIdIsNullAndActiveTrue(USER_ID)).thenReturn(List.of(
                category(2L, "Transporte", TransactionType.EXPENSE, "#3B82F6"),
                category(1L, "Comida", TransactionType.EXPENSE, "#EF4444")));

        List<CategoryResponse> result = categoryService.list(USER_ID, null, null, false);

        assertThat(result).extracting(CategoryResponse::name)
                .containsExactly("Comida", "Transporte");
        assertThat(result.getFirst().id()).isEqualTo(1L);
        assertThat(result.getFirst().active()).isTrue();
    }

    @Test
    void listWithIncludeInactiveReturnsAllCategories() {
        Category inactive = category(3L, "Antigua", TransactionType.EXPENSE, "#6B7280");
        inactive.setActive(false);
        when(categoryRepository.findByUserIdAndSpaceIdIsNull(USER_ID)).thenReturn(List.of(
                category(1L, "Comida", TransactionType.EXPENSE, "#EF4444"), inactive));

        List<CategoryResponse> result = categoryService.list(USER_ID, null, null, true);

        assertThat(result).extracting(CategoryResponse::name)
                .containsExactly("Antigua", "Comida");
        verify(categoryRepository, never()).findByUserIdAndSpaceIdIsNullAndActiveTrue(any());
    }

    @Test
    void listFiltersByType() {
        when(categoryRepository.findByUserIdAndSpaceIdIsNullAndActiveTrue(USER_ID)).thenReturn(List.of(
                category(1L, "Comida", TransactionType.EXPENSE, "#EF4444"),
                category(2L, "Nómina", TransactionType.INCOME, "#22C55E")));

        List<CategoryResponse> result = categoryService.list(USER_ID, null, TransactionType.INCOME, false);

        assertThat(result).extracting(CategoryResponse::name).containsExactly("Nómina");
        assertThat(result.getFirst().type()).isEqualTo(TransactionType.INCOME);
    }

    // --- create ---

    @Test
    void createSavesCategoryAndReturnsResponse() {
        var request = new CategoryRequest("Gimnasio", TransactionType.EXPENSE, "#FF8800", null, null, null, null);
        when(categoryRepository.existsByUserIdAndNameAndTypeAndSpaceIdIsNull(USER_ID, "Gimnasio", TransactionType.EXPENSE))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenReturn(category(10L, "Gimnasio", TransactionType.EXPENSE, "#FF8800"));

        CategoryResponse response = categoryService.create(USER_ID, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Gimnasio");
        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.color()).isEqualTo("#FF8800");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createSetsFixedWhenRequested() {
        var request = new CategoryRequest("Alquiler", TransactionType.EXPENSE, "#FF8800", null, null, true, null);
        when(categoryRepository.existsByUserIdAndNameAndTypeAndSpaceIdIsNull(USER_ID, "Alquiler", TransactionType.EXPENSE))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.create(USER_ID, request);

        assertThat(response.fixed()).isTrue();
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().isFixed()).isTrue();
    }

    @Test
    void createDefaultsFixedToFalseWhenNotProvided() {
        var request = new CategoryRequest("Ocio", TransactionType.EXPENSE, "#FF8800", null, null, null, null);
        when(categoryRepository.existsByUserIdAndNameAndTypeAndSpaceIdIsNull(USER_ID, "Ocio", TransactionType.EXPENSE))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.create(USER_ID, request);

        assertThat(response.fixed()).isFalse();
    }

    @Test
    void createWithDuplicateNameAndTypeThrowsConflict() {
        var request = new CategoryRequest("Comida", TransactionType.EXPENSE, "#FF8800", null, null, null, null);
        when(categoryRepository.existsByUserIdAndNameAndTypeAndSpaceIdIsNull(USER_ID, "Comida", TransactionType.EXPENSE))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(USER_ID, request))
                .isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createInSpaceChecksSpaceUniquenessAndMembership() {
        when(categoryRepository.existsBySpaceIdAndNameAndType(7L, "Cena fuera", TransactionType.EXPENSE))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        categoryService.create(USER_ID, new CategoryRequest("Cena fuera", TransactionType.EXPENSE, "#EF4444", null, 7L, null, null));

        verify(spaceAccess).assertActiveMember(USER_ID, 7L);
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSpaceId()).isEqualTo(7L);
    }

    @Test
    void createInSpaceConflictsOnDuplicateNameWithinSpace() {
        when(categoryRepository.existsBySpaceIdAndNameAndType(7L, "Comida", TransactionType.EXPENSE))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(USER_ID,
                new CategoryRequest("Comida", TransactionType.EXPENSE, "#EF4444", null, 7L, null, null)))
                .isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).save(any());
    }

    // --- update ---

    @Test
    void updateRenamesChangesColorAndDeactivates() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByUserIdAndNameAndTypeAndSpaceIdIsNull(USER_ID, "Alimentación", TransactionType.EXPENSE))
                .thenReturn(false);
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryResponse response = categoryService.update(USER_ID, 5L,
                new CategoryUpdateRequest("Alimentación", "#00FF00", null, false, null, null));

        assertThat(response.name()).isEqualTo("Alimentación");
        assertThat(response.color()).isEqualTo("#00FF00");
        assertThat(response.active()).isFalse();
        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void updateCanSetFixedFlag() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryResponse response = categoryService.update(USER_ID, 5L,
                new CategoryUpdateRequest("Comida", "#EF4444", null, null, true, null));

        assertThat(response.fixed()).isTrue();
    }

    @Test
    void updateWithNullFixedKeepsCurrentValue() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        existing.setFixed(true);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryResponse response = categoryService.update(USER_ID, 5L,
                new CategoryUpdateRequest("Comida", "#EF4444", null, null, null, null));

        assertThat(response.fixed()).isTrue();
    }

    @Test
    void updateWithNullActiveKeepsCurrentValue() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryResponse response = categoryService.update(USER_ID, 5L,
                new CategoryUpdateRequest("Comida", "#00FF00", null, null, null, null));

        assertThat(response.active()).isTrue();
    }

    @Test
    void updateKeepingSameNameDoesNotConflict() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryResponse response = categoryService.update(USER_ID, 5L,
                new CategoryUpdateRequest("Comida", "#123456", null, null, null, null));

        assertThat(response.color()).isEqualTo("#123456");
        verify(categoryRepository, never()).existsByUserIdAndNameAndTypeAndSpaceIdIsNull(any(), any(), any());
    }

    @Test
    void updateToDuplicateNameThrowsConflict() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByUserIdAndNameAndTypeAndSpaceIdIsNull(USER_ID, "Transporte", TransactionType.EXPENSE))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.update(USER_ID, 5L,
                new CategoryUpdateRequest("Transporte", "#EF4444", null, null, null, null)))
                .isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategoryOfAnotherUserThrowsNotFound() {
        Category others = new Category(2L, "Comida", TransactionType.EXPENSE, "#EF4444");
        ReflectionTestUtils.setField(others, "id", 99L);
        when(categoryRepository.findById(99L)).thenReturn(Optional.of(others));

        assertThatThrownBy(() -> categoryService.update(USER_ID, 99L,
                new CategoryUpdateRequest("Comida", "#EF4444", null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    // --- delete ---

    @Test
    void deleteWithoutTransactionsRemovesCategory() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(transactionRepository.existsByCategoryId(5L)).thenReturn(false);

        categoryService.delete(USER_ID, 5L);

        verify(categoryRepository).delete(existing);
    }

    @Test
    void deleteWithTransactionsDeactivatesInsteadOfRemoving() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(transactionRepository.existsByCategoryId(5L)).thenReturn(true);

        categoryService.delete(USER_ID, 5L);

        assertThat(existing.isActive()).isFalse();
        verify(categoryRepository).save(existing);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategoryOfAnotherUserThrowsNotFound() {
        Category others = new Category(2L, "Comida", TransactionType.EXPENSE, "#EF4444");
        ReflectionTestUtils.setField(others, "id", 99L);
        when(categoryRepository.findById(99L)).thenReturn(Optional.of(others));

        assertThatThrownBy(() -> categoryService.delete(USER_ID, 99L))
                .isInstanceOf(NotFoundException.class);
        verify(categoryRepository, never()).delete(any());
    }

    // --- budget / assign / setTarget (space-aware) ---

    @Test
    void budgetForSpaceUsesSpaceCategoriesAccountsAndSpend() {
        Category cat = new Category(USER_ID, "Comida", TransactionType.EXPENSE, "#EF4444");
        ReflectionTestUtils.setField(cat, "id", 3L);
        cat.setSpaceId(7L);
        when(categoryRepository.findBySpaceIdAndActiveTrue(7L)).thenReturn(List.of(cat));
        when(categoryRepository.findBySpaceId(7L)).thenReturn(List.of(cat));
        when(categoryBalanceRepository.findByCategoryIdIn(List.of(3L))).thenReturn(List.of());
        when(transactionRepository.sumExpenseByCategoryForSpace(eq(7L), any(), any())).thenReturn(List.of());
        when(accountRepository.sumActiveBalanceBySpaceIds(List.of(7L))).thenReturn(new BigDecimal("300.00"));

        CategoryBudgetResponse budget = categoryService.budget(USER_ID, 7L);

        verify(spaceAccess).assertActiveMember(USER_ID, 7L);
        assertThat(budget.totalAccounts()).isEqualByComparingTo("300.00");
        assertThat(budget.categories()).hasSize(1);
    }

    @Test
    void assignInSpaceAdjustsSharedEnvelopeAfterMembershipCheck() {
        Category cat = new Category(USER_ID, "Comida", TransactionType.EXPENSE, "#EF4444");
        ReflectionTestUtils.setField(cat, "id", 3L);
        cat.setSpaceId(7L);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(cat));
        when(categoryBalanceRepository.findByCategoryId(3L)).thenReturn(Optional.empty());
        // budget() re-fetch after assign:
        when(categoryRepository.findBySpaceIdAndActiveTrue(7L)).thenReturn(List.of(cat));
        when(categoryRepository.findBySpaceId(7L)).thenReturn(List.of(cat));
        when(categoryBalanceRepository.findByCategoryIdIn(any())).thenReturn(List.of());
        when(transactionRepository.sumExpenseByCategoryForSpace(eq(7L), any(), any())).thenReturn(List.of());
        when(accountRepository.sumActiveBalanceBySpaceIds(List.of(7L))).thenReturn(new BigDecimal("100.00"));

        categoryService.assign(USER_ID, 3L, 7L, new AssignRequest(new BigDecimal("50.00")));

        // membership is checked twice: findAccessible on the space category + the budget() re-fetch
        verify(spaceAccess, times(2)).assertActiveMember(USER_ID, 7L);
        ArgumentCaptor<CategoryBalance> captor = ArgumentCaptor.forClass(CategoryBalance.class);
        verify(categoryBalanceRepository).save(captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo("50.00");
    }
}
