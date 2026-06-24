package dev.jordi.senda.transaction;

import java.math.BigDecimal;

/**
 * Total spent in a single category over a date range. Used by the budget view
 * to show how much of each envelope has already been used this month.
 */
public record CategorySpent(Long categoryId, BigDecimal spent) {
}
