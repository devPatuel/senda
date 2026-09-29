package dev.jordi.senda.investment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Holding view with derived figures computed by the service:
 * <ul>
 *   <li>{@code cost} = quantity * avgCost (what you paid in)</li>
 *   <li>{@code marketValue} = quantity * currentPrice, or {@code null} when unpriced</li>
 *   <li>{@code pnl} = marketValue - cost, or {@code null} when unpriced</li>
 *   <li>{@code pnlPct} = pnl / cost * 100, or {@code null} when unpriced or cost is 0</li>
 *   <li>{@code rewardsCost} = cost of the REWARD lots (part of {@code cost})</li>
 * </ul>
 */
public record HoldingResponse(
        Long id,
        Long assetClassId,
        String assetClassName,
        PricingSource pricingSource,
        String symbol,
        String name,
        BigDecimal quantity,
        BigDecimal avgCost,
        BigDecimal currentPrice,
        Instant lastPricedAt,
        BigDecimal marketValue,
        BigDecimal pnl,
        BigDecimal cost,
        BigDecimal pnlPct,
        BigDecimal rewardsCost) {
}
