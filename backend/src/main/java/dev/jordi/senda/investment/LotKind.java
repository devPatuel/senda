package dev.jordi.senda.investment;

/**
 * Kind of lot. Rewards (staking, interest) enter the weighted-average cost at their
 * market value when received, exactly like a buy. A sell removes quantity at the
 * current average cost, so the average of what remains does not change.
 */
public enum LotKind {
    BUY,
    REWARD,
    SELL
}
