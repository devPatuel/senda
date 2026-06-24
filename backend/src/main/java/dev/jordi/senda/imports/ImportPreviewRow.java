package dev.jordi.senda.imports;

import dev.jordi.senda.common.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A preview row: the normalized transaction (positive amount + derived type), the
 * category suggested by a matching rule (if any), and whether it duplicates an
 * existing transaction.
 */
public record ImportPreviewRow(
        LocalDate date,
        String description,
        BigDecimal amount,
        TransactionType type,
        Long suggestedCategoryId,
        String suggestedCategoryName,
        boolean duplicate) {
}
