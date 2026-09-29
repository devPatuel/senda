package dev.jordi.senda.investment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the crypto price providers into a single {@link CryptoPriceProvider} bean.
 * Keeping exactly one bean of that type lets tests replace it with
 * {@code @MockitoBean} by type without ambiguity.
 */
@Configuration
class PricingConfig {

    @Bean
    CryptoPriceProvider cryptoPriceProvider() {
        return new FallbackCryptoPriceProvider(new CoinGeckoPriceProvider(), new CoinbasePriceProvider());
    }
}
