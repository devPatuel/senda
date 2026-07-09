package dev.jordi.senda.category;

import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import dev.jordi.senda.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

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

        List<CategoryResponse> result = categoryService.list(USER_ID, null, false);

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

        List<CategoryResponse> result = categoryService.list(USER_ID, null, true);

        assertThat(result).extracting(CategoryResponse::name)
                .containsExactly("Antigua", "Comida");
        verify(categoryRepository, never()).findByUserIdAndSpaceIdIsNullAndActiveTrue(any());
    }

    @Test
    void listFiltersByType() {
        when(categoryRepository.findByUserIdAndSpaceIdIsNullAndActiveTrue(USER_ID)).thenReturn(List.of(
                category(1L, "Comida", TransactionType.EXPENSE, "#EF4444"),
                category(2L, "Nómina", TransactionType.INCOME, "#22C55E")));

        List<CategoryResponse> result = categoryService.list(USER_ID, TransactionType.INCOME, false);

        assertThat(result).extracting(CategoryResponse::name).containsExactly("Nómina");
        assertThat(result.getFirst().type()).isEqualTo(TransactionType.INCOME);
    }

    // --- create ---

    @Test
    void createSavesCategoryAndReturnsResponse() {
        var request = new CategoryRequest("Gimnasio", TransactionType.EXPENSE, "#FF8800");
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
    void createWithDuplicateNameAndTypeThrowsConflict() {
        var request = new CategoryRequest("Comida", TransactionType.EXPENSE, "#FF8800");
        when(categoryRepository.existsByUserIdAndNameAndTypeAndSpaceIdIsNull(USER_ID, "Comida", TransactionType.EXPENSE))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(USER_ID, request))
                .isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).save(any());
    }

    // --- update ---

    @Test
    void updateRenamesChangesColorAndDeactivates() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(5L, USER_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByUserIdAndNameAndTypeAndSpaceIdIsNull(USER_ID, "Alimentación", TransactionType.EXPENSE))
                .thenReturn(false);
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryResponse response = categoryService.update(USER_ID, 5L,
                new CategoryUpdateRequest("Alimentación", "#00FF00", false));

        assertThat(response.name()).isEqualTo("Alimentación");
        assertThat(response.color()).isEqualTo("#00FF00");
        assertThat(response.active()).isFalse();
        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void updateWithNullActiveKeepsCurrentValue() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(5L, USER_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryResponse response = categoryService.update(USER_ID, 5L,
                new CategoryUpdateRequest("Comida", "#00FF00", null));

        assertThat(response.active()).isTrue();
    }

    @Test
    void updateKeepingSameNameDoesNotConflict() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(5L, USER_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryResponse response = categoryService.update(USER_ID, 5L,
                new CategoryUpdateRequest("Comida", "#123456", null));

        assertThat(response.color()).isEqualTo("#123456");
        verify(categoryRepository, never()).existsByUserIdAndNameAndTypeAndSpaceIdIsNull(any(), any(), any());
    }

    @Test
    void updateToDuplicateNameThrowsConflict() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(5L, USER_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByUserIdAndNameAndTypeAndSpaceIdIsNull(USER_ID, "Transporte", TransactionType.EXPENSE))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.update(USER_ID, 5L,
                new CategoryUpdateRequest("Transporte", "#EF4444", null)))
                .isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategoryOfAnotherUserThrowsNotFound() {
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(USER_ID, 99L,
                new CategoryUpdateRequest("Comida", "#EF4444", null)))
                .isInstanceOf(NotFoundException.class);
    }

    // --- delete ---

    @Test
    void deleteWithoutTransactionsRemovesCategory() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(5L, USER_ID)).thenReturn(Optional.of(existing));
        when(transactionRepository.existsByCategoryId(5L)).thenReturn(false);

        categoryService.delete(USER_ID, 5L);

        verify(categoryRepository).delete(existing);
    }

    @Test
    void deleteWithTransactionsDeactivatesInsteadOfRemoving() {
        Category existing = category(5L, "Comida", TransactionType.EXPENSE, "#EF4444");
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(5L, USER_ID)).thenReturn(Optional.of(existing));
        when(transactionRepository.existsByCategoryId(5L)).thenReturn(true);

        categoryService.delete(USER_ID, 5L);

        assertThat(existing.isActive()).isFalse();
        verify(categoryRepository).save(existing);
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategoryOfAnotherUserThrowsNotFound() {
        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(USER_ID, 99L))
                .isInstanceOf(NotFoundException.class);
        verify(categoryRepository, never()).delete(any());
    }
}
