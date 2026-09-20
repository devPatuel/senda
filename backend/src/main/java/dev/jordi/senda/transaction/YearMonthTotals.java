package dev.jordi.senda.transaction;

import java.math.BigDecimal;

/**
 * One month of the year view: what was spent and what was moved in as
 * transfers. They are kept apart because they answer different questions —
 * what the month cost, and what was put in to pay for it.
 */
public record YearMonthTotals(
        int year,
        int month,
        BigDecimal expense,
        BigDecimal transfersIn) {
}
