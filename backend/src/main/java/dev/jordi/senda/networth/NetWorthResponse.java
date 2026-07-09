package dev.jordi.senda.networth;

import java.math.BigDecimal;

/**
 * Snapshot of the authenticated user's net worth, broken down by asset type.
 * All amounts are in EUR with scale 2.
 */
public record NetWorthResponse(
        BigDecimal liquid,
        BigDecimal investments,
        BigDecimal investmentsHoldings,
        BigDecimal investmentsNfts,
        BigDecimal debtsInFavor,
        BigDecimal debtsAgainst,
        BigDecimal net,
        BigDecimal coupleShare) {
}
