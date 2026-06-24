package dev.jordi.senda.alerts;

import java.math.BigDecimal;

/**
 * "Ant expenses": a category with many small purchases this month that add up.
 */
public record AntExpenseAlert(
        Long categoryId,
        String categoryName,
        String categoryColor,
        long count,
        BigDecimal total) {
}
