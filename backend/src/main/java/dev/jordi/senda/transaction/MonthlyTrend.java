package dev.jordi.senda.transaction;

import java.math.BigDecimal;

/**
 * Income, expense and balance for a single month in the trends series.
 */
public record MonthlyTrend(
        int year,
        int month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance) {
}
