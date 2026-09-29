package dev.jordi.senda.investment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the current EUR price for a holding by its {@link PricingSource}, with
 * a short in-memory cache so a price refresh of many holdings (or repeated NFT
 * valuations) does not hammer the upstream provider (CoinGecko has tight rate
 * limits on the free tier).
 *
 * <p>Only {@code CRYPTO} fetches a live price. {@code METAL}, {@code FUND} and
 * {@code MANUAL} are user-maintained — no public, key-free, EUR-denominated API
 * was found that we trust for gold/silver spot or fund NAV by ISIN, so those
 * prices are typed in by hand (see {@code PricingSource}). This is intentional
 * and documented in an ADR.
 */
@Service
public class PricingService {

    static final Duration TTL = Duration.ofMinutes(15);
    // Negative results are cached for a much shorter time: long enough to stop a
    // refresh loop (or a junk/unmapped symbol) from hammering CoinGecko on every
    // call, short enough that a briefly-down provider is retried soon.
    static final Duration NEGATIVE_TTL = Duration.ofMinutes(2);

    private final CryptoPriceProvider cryptoPriceProvider;
    private final ConcurrentHashMap<String, CachedPrice> cache = new ConcurrentHashMap<>();

    public PricingService(CryptoPriceProvider cryptoPriceProvider) {
        this.cryptoPriceProvider = cryptoPriceProvider;
    }

    /** A {@code null} price is a cached miss (negative caching). */
    private record CachedPrice(BigDecimal price, Instant fetchedAt) {
    }

    /**
     * Current EUR price for {@code symbol} under {@code source}, or empty when the
     * source is user-maintained (METAL/FUND/MANUAL) or no price is available.
     */
    public Optional<BigDecimal> priceInEur(PricingSource source, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pricesInEur(source, List.of(symbol)).get(symbol.toUpperCase(Locale.ROOT)));
    }

    /**
     * Current EUR prices for {@code symbols}, keyed by upper-case symbol; symbols
     * without a price are absent. Cached symbols are served locally and all the
     * others go upstream in a single provider call.
     */
    public Map<String, BigDecimal> pricesInEur(PricingSource source, Collection<String> symbols) {
        if (source != PricingSource.CRYPTO || symbols == null) {
            // METAL/FUND/MANUAL are not auto-priced: the stored manual price wins
            return Map.of();
        }
        Map<String, BigDecimal> result = new HashMap<>();
        Set<String> toFetch = new HashSet<>();
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            String normalized = symbol.toUpperCase(Locale.ROOT);
            CachedPrice cached = cache.get(cacheKey(source, normalized));
            if (cached != null && !isExpired(cached)) {
                if (cached.price() != null) {
                    result.put(normalized, cached.price());
                }
            } else {
                toFetch.add(normalized);
            }
        }
        if (!toFetch.isEmpty()) {
            Map<String, BigDecimal> fresh = cryptoPriceProvider.pricesInEur(Set.copyOf(toFetch));
            Instant now = Instant.now();
            for (String symbol : toFetch) {
                BigDecimal price = fresh.get(symbol);
                // Cache hits for TTL and misses for the shorter NEGATIVE_TTL, so a junk or
                // unmapped symbol cannot trigger an upstream call on every single refresh.
                cache.put(cacheKey(source, symbol), new CachedPrice(price, now));
                if (price != null) {
                    result.put(symbol, price);
                }
            }
        }
        return result;
    }

    private boolean isExpired(CachedPrice cached) {
        Duration ttl = cached.price() != null ? TTL : NEGATIVE_TTL;
        return Duration.between(cached.fetchedAt(), Instant.now()).compareTo(ttl) > 0;
    }

    private static String cacheKey(PricingSource source, String symbol) {
        return source + ":" + symbol.toUpperCase(Locale.ROOT);
    }
}
