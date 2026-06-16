package dev.jordi.senda.investment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@link CryptoPriceProvider} backed by CoinGecko's free public API (no API key).
 *
 * <p>Endpoint: {@code GET /simple/price?ids={id}&vs_currencies=eur}. CoinGecko
 * keys on its own coin ids, not ticker symbols, so we map common tickers to ids.
 * Unmapped symbols fall back to the lowercased symbol as id (works for some
 * coins; harmlessly returns empty otherwise).
 *
 * <p>Network/timeout/parse errors never propagate: they are logged and mapped to
 * {@code Optional.empty()} so a price refresh degrades gracefully.
 */
@Component
public class CoinGeckoPriceProvider implements CryptoPriceProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoPriceProvider.class);

    private static final String BASE_URL = "https://api.coingecko.com/api/v3";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // Ticker -> CoinGecko coin id. Covers the common coins; extend as needed.
    private static final Map<String, String> SYMBOL_TO_ID = Map.ofEntries(
            Map.entry("BTC", "bitcoin"),
            Map.entry("ETH", "ethereum"),
            Map.entry("SOL", "solana"),
            Map.entry("ADA", "cardano"),
            Map.entry("XRP", "ripple"),
            Map.entry("DOGE", "dogecoin"),
            Map.entry("DOT", "polkadot"),
            Map.entry("MATIC", "matic-network"),
            Map.entry("BNB", "binancecoin"),
            Map.entry("LTC", "litecoin"),
            Map.entry("USDT", "tether"),
            Map.entry("USDC", "usd-coin"));

    private final RestClient restClient;

    public CoinGeckoPriceProvider() {
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(TIMEOUT)
                        .withReadTimeout(TIMEOUT));
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Optional<BigDecimal> priceInEur(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        String id = SYMBOL_TO_ID.getOrDefault(symbol.toUpperCase(Locale.ROOT), symbol.toLowerCase(Locale.ROOT));
        try {
            // Response shape: { "bitcoin": { "eur": 58000.0 } }
            Map<String, Map<String, BigDecimal>> body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/simple/price")
                            .queryParam("ids", id)
                            .queryParam("vs_currencies", "eur")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (body == null) {
                return Optional.empty();
            }
            Map<String, BigDecimal> prices = body.get(id);
            if (prices == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(prices.get("eur"));
        } catch (Exception ex) {
            // Network down, timeout, rate limit, malformed body: degrade to "no price"
            log.warn("CoinGecko price lookup failed for symbol {} (id {}): {}", symbol, id, ex.getMessage());
            return Optional.empty();
        }
    }
}
