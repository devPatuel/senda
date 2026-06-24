package dev.jordi.senda.investment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
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
        if (source != PricingSource.CRYPTO || symbol == null || symbol.isBlank()) {
            // METAL/FUND/MANUAL are not auto-priced: the stored manual price wins
            return Optional.empty();
        }
        String key = cacheKey(source, symbol);
        CachedPrice cached = cache.get(key);
        if (cached != null && !isExpired(cached)) {
            return Optional.ofNullable(cached.price());
        }
        Optional<BigDecimal> fresh = cryptoPriceProvider.priceInEur(symbol);
        // Cache hits for TTL and misses for the shorter NEGATIVE_TTL, so a junk or
        // unmapped symbol cannot trigger an upstream call on every single refresh.
        cache.put(key, new CachedPrice(fresh.orElse(null), Instant.now()));
        return fresh;
    }

    private boolean isExpired(CachedPrice cached) {
        Duration ttl = cached.price() != null ? TTL : NEGATIVE_TTL;
        return Duration.between(cached.fetchedAt(), Instant.now()).compareTo(ttl) > 0;
    }

    private static String cacheKey(PricingSource source, String symbol) {
        return source + ":" + symbol.toUpperCase(Locale.ROOT);
    }
}
