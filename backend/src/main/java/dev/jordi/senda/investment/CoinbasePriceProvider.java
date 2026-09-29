package dev.jordi.senda.investment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link CryptoPriceProvider} backed by Coinbase's public exchange-rates endpoint
 * (no API key). Used as a fallback for symbols CoinGecko did not price: CoinGecko
 * blocks some residential IPs outright (403 from CloudFront).
 *
 * <p>One request returns every currency quoted against EUR, as "units of X per
 * 1 EUR"; the EUR price is therefore {@code 1 / rate}. The URL is constant, so no
 * user input ever reaches the outbound request.
 *
 * <p>Errors never propagate: they are logged and mapped to "no price".
 */
public class CoinbasePriceProvider implements CryptoPriceProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinbasePriceProvider.class);

    private static final String URL = "https://api.coinbase.com/v2/exchange-rates?currency=EUR";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int PRICE_SCALE = 8;

    private final RestClient restClient;

    public CoinbasePriceProvider() {
        this(RestClient.builder().requestFactory(ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(TIMEOUT)
                        .withReadTimeout(TIMEOUT))));
    }

    CoinbasePriceProvider(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    // Response shape: {"data":{"currency":"EUR","rates":{"BTC":"0.0000135", ...}}}
    private record Rates(Map<String, String> rates) {
    }

    private record Body(Rates data) {
    }

    @Override
    public Map<String, BigDecimal> pricesInEur(Set<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        try {
            Body body = restClient.get().uri(URL).retrieve().body(Body.class);
            if (body == null || body.data() == null || body.data().rates() == null) {
                return Map.of();
            }
            Map<String, BigDecimal> result = new HashMap<>();
            for (String symbol : symbols) {
                BigDecimal rate = parse(body.data().rates().get(symbol));
                if (rate != null && rate.signum() > 0) {
                    result.put(symbol, BigDecimal.ONE.divide(rate, PRICE_SCALE, RoundingMode.HALF_UP));
                }
            }
            return result;
        } catch (Exception ex) {
            // Network down, timeout, HTTP error, malformed body: degrade to "no price"
            log.warn("Coinbase price lookup failed for {}: {}", symbols, ex.getMessage());
            return Map.of();
        }
    }

    private static BigDecimal parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
