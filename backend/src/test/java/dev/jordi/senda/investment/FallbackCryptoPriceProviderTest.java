package dev.jordi.senda.investment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FallbackCryptoPriceProviderTest {

    @Mock
    private CryptoPriceProvider primary;

    @Mock
    private CryptoPriceProvider secondary;

    @Test
    void asksSecondaryOnlyForSymbolsPrimaryMissed() {
        when(primary.pricesInEur(Set.of("BTC", "CRO"))).thenReturn(Map.of("BTC", new BigDecimal("58000")));
        when(secondary.pricesInEur(Set.of("CRO"))).thenReturn(Map.of("CRO", new BigDecimal("0.06")));

        Map<String, BigDecimal> prices = new FallbackCryptoPriceProvider(primary, secondary)
                .pricesInEur(Set.of("BTC", "CRO"));

        assertThat(prices).containsOnly(
                Map.entry("BTC", new BigDecimal("58000")),
                Map.entry("CRO", new BigDecimal("0.06")));
    }

    @Test
    void doesNotCallSecondaryWhenPrimaryHasEverything() {
        when(primary.pricesInEur(Set.of("BTC"))).thenReturn(Map.of("BTC", new BigDecimal("58000")));

        new FallbackCryptoPriceProvider(primary, secondary).pricesInEur(Set.of("BTC"));

        verifyNoInteractions(secondary);
    }

    @Test
    void usesSecondaryForEverythingWhenPrimaryReturnsNothing() {
        when(primary.pricesInEur(Set.of("BTC", "ETH"))).thenReturn(Map.of());
        when(secondary.pricesInEur(Set.of("BTC", "ETH")))
                .thenReturn(Map.of("BTC", BigDecimal.ONE, "ETH", BigDecimal.TEN));

        Map<String, BigDecimal> prices = new FallbackCryptoPriceProvider(primary, secondary)
                .pricesInEur(Set.of("BTC", "ETH"));

        assertThat(prices).containsOnlyKeys("BTC", "ETH");
    }
}
