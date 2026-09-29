package dev.jordi.senda.investment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private CryptoPriceProvider cryptoPriceProvider;

    private PricingService service;

    @BeforeEach
    void setUp() {
        service = new PricingService(cryptoPriceProvider);
    }

    @Test
    void cryptoPriceIsFetchedAndReturned() {
        when(cryptoPriceProvider.pricesInEur(Set.of("BTC"))).thenReturn(Map.of("BTC", new BigDecimal("58000")));

        Optional<BigDecimal> price = service.priceInEur(PricingSource.CRYPTO, "BTC");

        assertThat(price).contains(new BigDecimal("58000"));
    }

    @Test
    void secondLookupHitsCacheAndDoesNotCallProviderAgain() {
        when(cryptoPriceProvider.pricesInEur(Set.of("BTC"))).thenReturn(Map.of("BTC", new BigDecimal("58000")));

        service.priceInEur(PricingSource.CRYPTO, "BTC");
        Optional<BigDecimal> second = service.priceInEur(PricingSource.CRYPTO, "BTC");

        assertThat(second).contains(new BigDecimal("58000"));
        // Provider invoked exactly once: the second call is served from cache
        verify(cryptoPriceProvider, times(1)).pricesInEur(Set.of("BTC"));
    }

    @Test
    void cacheKeyIsCaseInsensitiveBySymbol() {
        when(cryptoPriceProvider.pricesInEur(Set.of("BTC"))).thenReturn(Map.of("BTC", new BigDecimal("58000")));

        service.priceInEur(PricingSource.CRYPTO, "BTC");
        service.priceInEur(PricingSource.CRYPTO, "btc");

        verify(cryptoPriceProvider, times(1)).pricesInEur(Set.of("BTC"));
    }

    @Test
    void missIsNegativeCachedSoItIsNotRefetchedImmediately() {
        when(cryptoPriceProvider.pricesInEur(Set.of("BTC"))).thenReturn(Map.of());

        assertThat(service.priceInEur(PricingSource.CRYPTO, "BTC")).isEmpty();
        // Negative caching: a junk/unmapped symbol (or a briefly-down provider) is
        // not re-queried on the very next call, so a refresh loop cannot hammer the
        // upstream API. The negative entry expires after the shorter NEGATIVE_TTL.
        assertThat(service.priceInEur(PricingSource.CRYPTO, "BTC")).isEmpty();
        verify(cryptoPriceProvider, times(1)).pricesInEur(Set.of("BTC"));
    }

    @Test
    void batchLookupFetchesAllUncachedSymbolsInOneProviderCall() {
        when(cryptoPriceProvider.pricesInEur(Set.of("BTC", "ETH", "CRO")))
                .thenReturn(Map.of("BTC", new BigDecimal("58000"), "ETH", new BigDecimal("3000")));

        Map<String, BigDecimal> prices = service.pricesInEur(PricingSource.CRYPTO, List.of("btc", "ETH", "CRO", "BTC"));

        assertThat(prices).containsOnly(
                Map.entry("BTC", new BigDecimal("58000")),
                Map.entry("ETH", new BigDecimal("3000")));
        verify(cryptoPriceProvider, times(1)).pricesInEur(Set.of("BTC", "ETH", "CRO"));
    }

    @Test
    void batchLookupOnlyAsksProviderForSymbolsNotAlreadyCached() {
        when(cryptoPriceProvider.pricesInEur(Set.of("BTC"))).thenReturn(Map.of("BTC", new BigDecimal("58000")));
        when(cryptoPriceProvider.pricesInEur(Set.of("ETH"))).thenReturn(Map.of("ETH", new BigDecimal("3000")));
        service.priceInEur(PricingSource.CRYPTO, "BTC");

        Map<String, BigDecimal> prices = service.pricesInEur(PricingSource.CRYPTO, List.of("BTC", "ETH"));

        assertThat(prices).containsOnlyKeys("BTC", "ETH");
        verify(cryptoPriceProvider, times(1)).pricesInEur(Set.of("ETH"));
    }

    @Test
    void nonCryptoSourcesNeverCallTheProvider() {
        assertThat(service.priceInEur(PricingSource.METAL, "XAU")).isEmpty();
        assertThat(service.priceInEur(PricingSource.FUND, "IE00B4L5Y983")).isEmpty();
        assertThat(service.pricesInEur(PricingSource.MANUAL, List.of("WHATEVER"))).isEmpty();

        verifyNoInteractions(cryptoPriceProvider);
    }
}
