package dev.jordi.senda.shoppinglist;

import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.product.PriceEntry;
import dev.jordi.senda.product.PriceEntryRepository;
import dev.jordi.senda.product.Product;
import dev.jordi.senda.product.ProductRepository;
import dev.jordi.senda.product.UnitType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    private static final Long USER = 1L;

    @Mock private ShoppingListItemRepository listRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PriceEntryRepository priceEntryRepository;

    @InjectMocks private ShoppingListService service;

    private Product product(Long id, String name) {
        Product p = new Product(USER, name, UnitType.QUANTITY, new BigDecimal("1.000"), "ud");
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private ShoppingListItem item(Long id, Long productId, int qty) {
        ShoppingListItem i = new ShoppingListItem(USER, productId, qty);
        ReflectionTestUtils.setField(i, "id", id);
        return i;
    }

    private PriceEntry price(Long productId, String amount, String supermarket) {
        return new PriceEntry(productId, new BigDecimal(amount), supermarket);
    }

    @Test
    void get_sumsLineTotalsFromLatestPrice_skippingProductsWithoutPrice() {
        when(listRepository.findByUserId(USER)).thenReturn(List.of(
                item(1L, 10L, 3),   // 2.00 x 3 = 6.00
                item(2L, 20L, 1)));  // no price -> null
        lenient().when(productRepository.findByIdAndUserIdAndSpaceIdIsNull(10L, USER))
                .thenReturn(Optional.of(product(10L, "Leche")));
        lenient().when(productRepository.findByIdAndUserIdAndSpaceIdIsNull(20L, USER))
                .thenReturn(Optional.of(product(20L, "Pan")));
        when(priceEntryRepository.findTopByProductIdOrderByRecordedAtDesc(10L))
                .thenReturn(Optional.of(price(10L, "2.00", "Lidl")));
        when(priceEntryRepository.findTopByProductIdOrderByRecordedAtDesc(20L))
                .thenReturn(Optional.empty());

        ShoppingListResponse res = service.get(USER);

        assertThat(res.estimatedTotal()).isEqualByComparingTo("6.00");
        assertThat(res.items()).hasSize(2);
        assertThat(res.items().get(0).lineTotal()).isEqualByComparingTo("6.00");
        assertThat(res.items().get(1).lineTotal()).isNull();
    }

    @Test
    void add_foreignProduct_throwsNotFound() {
        when(productRepository.findByIdAndUserIdAndSpaceIdIsNull(99L, USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.add(USER, new AddToListRequest(99L, 1)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void add_duplicateProduct_throwsInvalid() {
        when(productRepository.findByIdAndUserIdAndSpaceIdIsNull(10L, USER))
                .thenReturn(Optional.of(product(10L, "Leche")));
        when(listRepository.findByUserIdAndProductId(USER, 10L))
                .thenReturn(Optional.of(item(5L, 10L, 1)));
        assertThatThrownBy(() -> service.add(USER, new AddToListRequest(10L, 1)))
                .isInstanceOf(InvalidShoppingListException.class);
    }

    @Test
    void update_foreignItem_throwsNotFound() {
        when(listRepository.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(USER, 99L, new UpdateListItemRequest(2, null)))
                .isInstanceOf(NotFoundException.class);
    }
}
