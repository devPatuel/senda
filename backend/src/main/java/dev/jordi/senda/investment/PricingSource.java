package dev.jordi.senda.investment;

/**
 * How a holding's price is obtained. Module-local: nothing outside the
 * investment module needs it.
 *
 * <ul>
 *   <li>{@code CRYPTO} — fetched from CoinGecko (EUR), see {@code PricingService}.</li>
 *   <li>{@code METAL} — manual: no reliable key-free EUR API was found for gold/silver.</li>
 *   <li>{@code FUND} — manual: no free reliable API by ISIN; the user types the NAV.</li>
 *   <li>{@code MANUAL} — the user sets the price by hand.</li>
 * </ul>
 */
public enum PricingSource {
    CRYPTO,
    METAL,
    FUND,
    MANUAL
}
