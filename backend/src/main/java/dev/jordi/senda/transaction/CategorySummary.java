package dev.jordi.senda.transaction;

import dev.jordi.senda.common.TransactionType;

import java.math.BigDecimal;

/**
 * Per-category aggregation row, built directly by a JPQL constructor expression.
 */
public record CategorySummary(
        Long categoryId,
        String categoryName,
        String categoryColor,
        TransactionType type,
        BigDecimal total) {
}
