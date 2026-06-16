package dev.jordi.senda.shopping;

import dev.jordi.senda.allocation.AllocationEnvelope;
import dev.jordi.senda.allocation.AllocationEnvelopeRepository;
import dev.jordi.senda.allocation.EnvelopeBalance;
import dev.jordi.senda.allocation.EnvelopeBalanceRepository;
import dev.jordi.senda.common.NotFoundException;
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
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ENVELOPE_ID = 10L;

    @Mock
    private ShoppingItemRepository shoppingItemRepository;

    @Mock
    private EnvelopeBalanceRepository envelopeBalanceRepository;

    @Mock
    private AllocationEnvelopeRepository allocationEnvelopeRepository;

    private ShoppingService service;

    @BeforeEach
    void setUp() {
        service = new ShoppingService(shoppingItemRepository, envelopeBalanceRepository, allocationEnvelopeRepository);
    }

    // --- helpers ---

    private static ShoppingItem groceryItem(Long id, String name) {
        ShoppingItem item = new ShoppingItem(USER_ID, ShoppingListType.GROCERY, name, null, null, null, null);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static ShoppingItem wishlistItem(Long id, String name, BigDecimal price, Long envelopeId) {
        ShoppingItem item = new ShoppingItem(USER_ID, ShoppingListType.WISHLIST, name, price, envelopeId, 1, null);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static AllocationEnvelope envelope(Long id, Long userId, String name) {
        AllocationEnvelope env = new AllocationEnvelope(userId, name, new BigDecimal("50.00"), 1);
        ReflectionTestUtils.setField(env, "id", id);
        return env;
    }

    private static EnvelopeBalance balance(Long envelopeId, Long userId, String amount) {
        EnvelopeBalance eb = new EnvelopeBalance(envelopeId, userId);
        eb.setBalance(new BigDecimal(amount));
        return eb;
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

        when(allocationEnvelopeRepository.findByIdAndUserId(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.of(envelope(ENVELOPE_ID, USER_ID, "Ahorro")));

        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(inv -> {
            ShoppingItem saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });

        when(envelopeBalanceRepository.findByEnvelopeId(ENVELOPE_ID))
                .thenReturn(Optional.of(balance(ENVELOPE_ID, USER_ID, "800.00")));
        when(allocationEnvelopeRepository.findByIdAndUserId(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.of(envelope(ENVELOPE_ID, USER_ID, "Ahorro")));

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

        when(allocationEnvelopeRepository.findByIdAndUserId(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.of(envelope(ENVELOPE_ID, USER_ID, "Ahorro")));

        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(inv -> {
            ShoppingItem saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        });

        when(envelopeBalanceRepository.findByEnvelopeId(ENVELOPE_ID))
                .thenReturn(Optional.of(balance(ENVELOPE_ID, USER_ID, "500.00")));
        when(allocationEnvelopeRepository.findByIdAndUserId(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.of(envelope(ENVELOPE_ID, USER_ID, "Ahorro")));

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

        when(allocationEnvelopeRepository.findByIdAndUserId(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.of(envelope(ENVELOPE_ID, USER_ID, "Ahorro")));

        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(inv -> {
            ShoppingItem saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        when(envelopeBalanceRepository.findByEnvelopeId(ENVELOPE_ID))
                .thenReturn(Optional.of(balance(ENVELOPE_ID, USER_ID, "200.00")));
        when(allocationEnvelopeRepository.findByIdAndUserId(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.of(envelope(ENVELOPE_ID, USER_ID, "Ahorro")));

        ShoppingItemResponse response = service.create(USER_ID, req);

        assertThat(response.feasible()).isNull();
    }

    // --- envelope belonging to another user -> 404 ---

    @Test
    void envelopeBelongingToAnotherUserThrowsNotFound() {
        ShoppingItemRequest req = new ShoppingItemRequest(
                ShoppingListType.WISHLIST, "NAS", new BigDecimal("400.00"), ENVELOPE_ID, null, null);

        when(allocationEnvelopeRepository.findByIdAndUserId(ENVELOPE_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER_ID, req))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Envelope not found");
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
