package dev.jordi.senda.product;

import dev.jordi.senda.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private ProductRepository productRepository;
    @Mock private PriceEntryRepository priceEntryRepository;

    @InjectMocks private ProductService service;

    private Product product(Long id, String name) {
        Product p = new Product(USER_ID, name, UnitType.QUANTITY, new BigDecimal("1.000"), "ud");
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private PriceEntry price(Long id, Long productId, String super_, String amount, Instant at) {
        PriceEntry e = new PriceEntry(productId, new BigDecimal(amount), super_);
        ReflectionTestUtils.setField(e, "id", id);
        ReflectionTestUtils.setField(e, "recordedAt", at);
        return e;
    }

    @Test
    void listBuildsCurrentPricePerSupermarketCheapestFirst() {
        Product p = product(10L, "Leche");
        when(productRepository.findByUserIdAndSpaceIdIsNullOrderByNameAsc(USER_ID))
                .thenReturn(List.of(p));
        // Mercadona has an older 1.30 and a newer 1.25 -> current = 1.25.
        // Lidl current = 1.10. Comparison must be [Lidl 1.10, Mercadona 1.25].
        when(priceEntryRepository.findByProductIdInOrderByRecordedAtDesc(anyList()))
                .thenReturn(List.of(
                        price(3L, 10L, "Mercadona", "1.25", Instant.parse("2026-07-10T10:00:00Z")),
                        price(2L, 10L, "Lidl", "1.10", Instant.parse("2026-07-09T10:00:00Z")),
                        price(1L, 10L, "Mercadona", "1.30", Instant.parse("2026-07-01T10:00:00Z"))));

        List<ProductResponse> result = service.list(USER_ID);

        assertThat(result).hasSize(1);
        List<CurrentPriceResponse> cp = result.get(0).currentPrices();
        assertThat(cp).extracting(CurrentPriceResponse::supermarket)
                .containsExactly("Lidl", "Mercadona");
        assertThat(cp.get(0).price()).isEqualByComparingTo("1.10");
        assertThat(cp.get(1).price()).isEqualByComparingTo("1.25");
    }

    @Test
    void addPriceOnForeignProductThrowsNotFound() {
        when(productRepository.findByIdAndUserIdAndSpaceIdIsNull(99L, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addPrice(USER_ID, 99L,
                new PriceEntryRequest(new BigDecimal("1.00"), "Lidl")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addPricePersistsAndReturnsEntry() {
        Product p = product(10L, "Leche");
        when(productRepository.findByIdAndUserIdAndSpaceIdIsNull(10L, USER_ID))
                .thenReturn(Optional.of(p));
        when(priceEntryRepository.save(any(PriceEntry.class))).thenAnswer(inv -> {
            PriceEntry e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 55L);
            ReflectionTestUtils.setField(e, "recordedAt", Instant.parse("2026-07-11T10:00:00Z"));
            return e;
        });

        PriceEntryResponse res = service.addPrice(USER_ID, 10L,
                new PriceEntryRequest(new BigDecimal("1.15"), "Lidl"));

        assertThat(res.id()).isEqualTo(55L);
        assertThat(res.supermarket()).isEqualTo("Lidl");
        assertThat(res.price()).isEqualByComparingTo("1.15");
    }

    @Test
    void historyOnForeignProductThrowsNotFound() {
        when(productRepository.findByIdAndUserIdAndSpaceIdIsNull(99L, USER_ID))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.priceHistory(USER_ID, 99L))
                .isInstanceOf(NotFoundException.class);
    }
}
