package dev.jordi.senda.investment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link CryptoPriceProvider} backed by CoinGecko's free public API (no API key).
 *
 * <p>Endpoint: {@code GET /simple/price?ids={id1},{id2}&vs_currencies=eur}, one
 * request for all symbols. CoinGecko
 * keys on its own coin ids, not ticker symbols, so we map common tickers to ids.
 * Unmapped symbols fall back to the lowercased symbol as id (works for some
 * coins; harmlessly returns empty otherwise).
 *
 * <p>Network/timeout/parse errors never propagate: they are logged and mapped to
 * "no price" so a price refresh degrades gracefully.
 */
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
            Map.entry("CRO", "crypto-com-chain"),
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

    /** CoinGecko coin id for a ticker (case-insensitive). */
    static String coinGeckoId(String symbol) {
        return SYMBOL_TO_ID.getOrDefault(symbol.toUpperCase(Locale.ROOT), symbol.toLowerCase(Locale.ROOT));
    }

    @Override
    public Map<String, BigDecimal> pricesInEur(Set<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        // Several tickers could share an id, so keep id -> symbols
        Map<String, List<String>> symbolsById = symbols.stream()
                .collect(Collectors.groupingBy(CoinGeckoPriceProvider::coinGeckoId));
        String ids = String.join(",", symbolsById.keySet());
        try {
            // Response shape: { "bitcoin": { "eur": 58000.0 }, "ethereum": { "eur": 3000.0 } }
            Map<String, Map<String, BigDecimal>> body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/simple/price")
                            .queryParam("ids", ids)
                            .queryParam("vs_currencies", "eur")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (body == null) {
                return Map.of();
            }
            Map<String, BigDecimal> result = new HashMap<>();
            symbolsById.forEach((id, idSymbols) -> {
                Map<String, BigDecimal> prices = body.get(id);
                BigDecimal eur = prices != null ? prices.get("eur") : null;
                if (eur != null) {
                    idSymbols.forEach(symbol -> result.put(symbol, eur));
                }
            });
            return result;
        } catch (Exception ex) {
            // Network down, timeout, rate limit, malformed body: degrade to "no price"
            log.warn("CoinGecko price lookup failed for ids {}: {}", ids, ex.getMessage());
            return Map.of();
        }
    }
}
