package dev.jordi.senda.investment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

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
        when(cryptoPriceProvider.priceInEur("BTC")).thenReturn(Optional.of(new BigDecimal("58000")));

        Optional<BigDecimal> price = service.priceInEur(PricingSource.CRYPTO, "BTC");

        assertThat(price).contains(new BigDecimal("58000"));
    }

    @Test
    void secondLookupHitsCacheAndDoesNotCallProviderAgain() {
        when(cryptoPriceProvider.priceInEur("BTC")).thenReturn(Optional.of(new BigDecimal("58000")));

        service.priceInEur(PricingSource.CRYPTO, "BTC");
        Optional<BigDecimal> second = service.priceInEur(PricingSource.CRYPTO, "BTC");

        assertThat(second).contains(new BigDecimal("58000"));
        // Provider invoked exactly once: the second call is served from cache
        verify(cryptoPriceProvider, times(1)).priceInEur("BTC");
    }

    @Test
    void cacheKeyIsCaseInsensitiveBySymbol() {
        when(cryptoPriceProvider.priceInEur("BTC")).thenReturn(Optional.of(new BigDecimal("58000")));

        service.priceInEur(PricingSource.CRYPTO, "BTC");
        service.priceInEur(PricingSource.CRYPTO, "btc");

        verify(cryptoPriceProvider, times(1)).priceInEur("BTC");
    }

    @Test
    void missIsNegativeCachedSoItIsNotRefetchedImmediately() {
        when(cryptoPriceProvider.priceInEur("BTC")).thenReturn(Optional.empty());

        assertThat(service.priceInEur(PricingSource.CRYPTO, "BTC")).isEmpty();
        // Negative caching: a junk/unmapped symbol (or a briefly-down provider) is
        // not re-queried on the very next call, so a refresh loop cannot hammer the
        // upstream API. The negative entry expires after the shorter NEGATIVE_TTL.
        assertThat(service.priceInEur(PricingSource.CRYPTO, "BTC")).isEmpty();
        verify(cryptoPriceProvider, times(1)).priceInEur("BTC");
    }

    @Test
    void nonCryptoSourcesNeverCallTheProvider() {
        assertThat(service.priceInEur(PricingSource.METAL, "XAU")).isEmpty();
        assertThat(service.priceInEur(PricingSource.FUND, "IE00B4L5Y983")).isEmpty();
        assertThat(service.priceInEur(PricingSource.MANUAL, "WHATEVER")).isEmpty();

        verifyNoInteractions(cryptoPriceProvider);
    }
}
