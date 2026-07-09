package dev.jordi.senda.shopping;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryBalance;
import dev.jordi.senda.category.CategoryBalanceRepository;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingServiceTest {

    private static final Long USER_ID = 1L;
    // A shopping item's "envelope" is an expense category; this is its id.
    private static final Long ENVELOPE_ID = 10L;

    @Mock
    private ShoppingItemRepository shoppingItemRepository;

    @Mock
    private CategoryBalanceRepository categoryBalanceRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private ShoppingService service;

    @BeforeEach
    void setUp() {
        service = new ShoppingService(shoppingItemRepository, categoryBalanceRepository, categoryRepository);
    }

    // --- helpers ---

    private static ShoppingItem groceryItem(Long id, String name) {
        ShoppingItem item = new ShoppingItem(USER_ID, ShoppingListType.GROCERY, name, null, null, null, null);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static Category expenseCategory(Long id, Long userId, String name) {
        Category c = new Category(userId, name, TransactionType.EXPENSE, "#10b981");
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static Category incomeCategory(Long id, Long userId, String name) {
        Category c = new Category(userId, name, TransactionType.INCOME, "#22c55e");
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static CategoryBalance balance(Long categoryId, Long userId, String amount) {
        CategoryBalance cb = new CategoryBalance(categoryId, userId);
        cb.setBalance(new BigDecimal(amount));
        return cb;
    }

    // --- create GROCERY ---

    @Test
    void createGroceryItemPersistsAndReturnsFeasibleNull() {
        ShoppingItemRequest req = new ShoppingItemRequest(
                ShoppingListType.GROCERY, "Leche", null, null, null, null);

        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(inv -> {
            ShoppingItem saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        ShoppingItemResponse response = service.create(USER_ID, req);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.listType()).isEqualTo(ShoppingListType.GROCERY);
        assertThat(response.name()).isEqualTo("Leche");
        assertThat(response.feasible()).isNull();
        assertThat(response.envelopeName()).isNull();
        assertThat(response.envelopeBalance()).isNull();

        ArgumentCaptor<ShoppingItem> captor = ArgumentCaptor.forClass(ShoppingItem.class);
        verify(shoppingItemRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().isBought()).isFalse();
    }

    // --- create WISHLIST feasible=true ---

    @Test
    void createWishlistWithEnvelopeReturnsFeasibleTrueWhenBalanceSufficient() {
        ShoppingItemRequest req = new ShoppingItemRequest(
                ShoppingListType.WISHLIST, "NAS", new BigDecimal("500.00"), ENVELOPE_ID, 1, null);

        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.of(expenseCategory(ENVELOPE_ID, USER_ID, "Ahorro")));
        when(categoryBalanceRepository.findByCategoryId(ENVELOPE_ID))
                .thenReturn(Optional.of(balance(ENVELOPE_ID, USER_ID, "800.00")));
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(inv -> {
            ShoppingItem saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });

        ShoppingItemResponse response = service.create(USER_ID, req);

        assertThat(response.feasible()).isTrue();
        assertThat(response.envelopeName()).isEqualTo("Ahorro");
        assertThat(response.envelopeBalance()).isEqualByComparingTo("800.00");
    }

    // --- create WISHLIST feasible=false ---

    @Test
    void createWishlistReturnsFeasibleFalseWhenBalanceInsufficient() {
        ShoppingItemRequest req = new ShoppingItemRequest(
                ShoppingListType.WISHLIST, "Coche", new BigDecimal("15000.00"), ENVELOPE_ID, 2, null);

        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.of(expenseCategory(ENVELOPE_ID, USER_ID, "Ahorro")));
        when(categoryBalanceRepository.findByCategoryId(ENVELOPE_ID))
                .thenReturn(Optional.of(balance(ENVELOPE_ID, USER_ID, "500.00")));
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(inv -> {
            ShoppingItem saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        });

        ShoppingItemResponse response = service.create(USER_ID, req);

        assertThat(response.feasible()).isFalse();
    }

    // --- feasible=null when no envelope ---

    @Test
    void wishlistWithoutEnvelopHasFeasibleNull() {
        ShoppingItemRequest req = new ShoppingItemRequest(
                ShoppingListType.WISHLIST, "Viaje", new BigDecimal("3000.00"), null, null, null);

        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(inv -> {
            ShoppingItem saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 4L);
            return saved;
        });

        ShoppingItemResponse response = service.create(USER_ID, req);

        assertThat(response.feasible()).isNull();
        assertThat(response.envelopeId()).isNull();
    }

    // --- feasible=null when no estimatedPrice ---

    @Test
    void wishlistWithEnvelopeButNoPriceHasFeasibleNull() {
        ShoppingItemRequest req = new ShoppingItemRequest(
                ShoppingListType.WISHLIST, "Casa", null, ENVELOPE_ID, null, null);

        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.of(expenseCategory(ENVELOPE_ID, USER_ID, "Ahorro")));
        when(categoryBalanceRepository.findByCategoryId(ENVELOPE_ID))
                .thenReturn(Optional.of(balance(ENVELOPE_ID, USER_ID, "200.00")));
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(inv -> {
            ShoppingItem saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        ShoppingItemResponse response = service.create(USER_ID, req);

        assertThat(response.feasible()).isNull();
    }

    // --- envelope belonging to another user -> 404 ---

    @Test
    void envelopeBelongingToAnotherUserThrowsNotFound() {
        ShoppingItemRequest req = new ShoppingItemRequest(
                ShoppingListType.WISHLIST, "NAS", new BigDecimal("400.00"), ENVELOPE_ID, null, null);

        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID, req))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Envelope not found");
    }

    // --- envelope that is an INCOME category is rejected ---

    @Test
    void envelopeThatIsIncomeCategoryThrows() {
        ShoppingItemRequest req = new ShoppingItemRequest(
                ShoppingListType.WISHLIST, "NAS", new BigDecimal("400.00"), ENVELOPE_ID, null, null);

        when(categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.of(incomeCategory(ENVELOPE_ID, USER_ID, "Nómina")));

        assertThatThrownBy(() -> service.create(USER_ID, req))
                .isInstanceOf(InvalidShoppingException.class);
    }

    // --- toggle bought ---

    @Test
    void setBoughtTogglesFieldAndReturnsUpdatedResponse() {
        ShoppingItem item = groceryItem(1L, "Pan");
        when(shoppingItemRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(item));

        ShoppingItemResponse response = service.setBought(USER_ID, 1L, true);

        assertThat(response.bought()).isTrue();
        assertThat(item.isBought()).isTrue();
    }

    @Test
    void setBoughtFalseUnchecksItem() {
        ShoppingItem item = groceryItem(1L, "Pan");
        item.setBought(true);
        when(shoppingItemRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(item));

        ShoppingItemResponse response = service.setBought(USER_ID, 1L, false);

        assertThat(response.bought()).isFalse();
    }

    // --- 404 for foreign item ---

    @Test
    void setBoughtOnForeignItemThrowsNotFound() {
        when(shoppingItemRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setBought(USER_ID, 99L, true))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Shopping item not found");
    }

    @Test
    void deleteOnForeignItemThrowsNotFound() {
        when(shoppingItemRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateRejectsChangingListType() {
        ShoppingItem grocery = groceryItem(5L, "Leche");
        when(shoppingItemRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(grocery));

        // Same item, but the request flips it to WISHLIST: must be rejected (400)
        ShoppingItemRequest req = new ShoppingItemRequest(
                ShoppingListType.WISHLIST, "Leche", null, null, null, null);

        assertThatThrownBy(() -> service.update(USER_ID, 5L, req))
                .isInstanceOf(InvalidShoppingException.class)
                .hasMessageContaining("listType");
    }
}
