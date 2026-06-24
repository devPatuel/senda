package dev.jordi.senda.transaction;

import java.math.BigDecimal;

/**
 * Count and total of expense transactions in a single category over a range,
 * aggregated in the database. Feeds the "small recurring expenses" heuristic.
 */
public record CategoryExpenseStat(
        Long categoryId,
        String categoryName,
        String categoryColor,
        Long count,
        BigDecimal total) {
}
