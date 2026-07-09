package dev.jordi.senda.networth;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day in the net-worth history series.
 */
public record NetWorthHistoryPoint(
        LocalDate date,
        BigDecimal net,
        BigDecimal liquid,
        BigDecimal investments,
        BigDecimal debtsInFavor,
        BigDecimal debtsAgainst,
        BigDecimal coupleShare) {
}
