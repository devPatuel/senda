package dev.jordi.senda.investment;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Fetches the current EUR price of a crypto symbol. Extracted as an interface so
 * {@code PricingService} can be unit-tested without hitting the network: tests
 * inject a mock, production uses {@link CoinGeckoPriceProvider}.
 */
public interface CryptoPriceProvider {

    /**
     * @param symbol e.g. "BTC", "ETH" (case-insensitive)
     * @return the price in EUR, or empty if unknown / unavailable / on error
     */
    Optional<BigDecimal> priceInEur(String symbol);
}
