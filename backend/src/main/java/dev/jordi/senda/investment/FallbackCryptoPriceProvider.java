package dev.jordi.senda.investment;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Asks the primary provider for every symbol and the secondary one only for the
 * symbols the primary could not price, so the fallback costs nothing while the
 * primary works.
 */
public class FallbackCryptoPriceProvider implements CryptoPriceProvider {

    private final CryptoPriceProvider primary;
    private final CryptoPriceProvider secondary;

    public FallbackCryptoPriceProvider(CryptoPriceProvider primary, CryptoPriceProvider secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public Map<String, BigDecimal> pricesInEur(Set<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> result = new HashMap<>(primary.pricesInEur(symbols));
        Set<String> missing = symbols.stream()
                .filter(symbol -> !result.containsKey(symbol))
                .collect(Collectors.toUnmodifiableSet());
        if (!missing.isEmpty()) {
            result.putAll(secondary.pricesInEur(missing));
        }
        return result;
    }
}
