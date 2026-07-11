package dev.jordi.senda.wishlist;

import dev.jordi.senda.common.NotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WishlistServiceTest {

    private final WishlistItemRepository repo = mock(WishlistItemRepository.class);
    private final WishlistService service = new WishlistService(repo);

    @Test
    void get_sumsPricesSkippingNulls_andSortsByPriority() {
        when(repo.findByUserId(1L)).thenReturn(List.of(
                new WishlistItem(1L, "B", null, null, null, new BigDecimal("30.00"), 2),
                new WishlistItem(1L, "A", null, null, null, null, 1),          // null price ignored
                new WishlistItem(1L, "C", null, null, null, new BigDecimal("70.00"), null)));

        WishlistResponse res = service.get(1L);

        assertThat(res.total()).isEqualByComparingTo("100.00");
        // priority asc nulls last, then name
        assertThat(res.items()).extracting(WishlistItemResponse::name)
                .containsExactly("A", "B", "C");
    }

    @Test
    void update_foreignItem_throwsNotFound() {
        when(repo.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(1L, 99L,
                new WishlistItemRequest("x", null, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }
}
