package dev.jordi.senda.investment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Fetches current EUR prices of crypto symbols. Extracted as an interface so
 * {@code PricingService} can be unit-tested without hitting the network: tests
 * inject a mock, production uses {@link CoinGeckoPriceProvider}.
 */
public interface CryptoPriceProvider {

    /**
     * Looks up several symbols at once, so a refresh costs one upstream request
     * instead of one per holding (free-tier rate limits are tight).
     *
     * @param symbols upper-case tickers, e.g. "BTC", "ETH"
     * @return price in EUR keyed by the requested symbol; symbols that are unknown,
     *         unavailable or failed are simply absent (never throws)
     */
    Map<String, BigDecimal> pricesInEur(Set<String> symbols);
}
