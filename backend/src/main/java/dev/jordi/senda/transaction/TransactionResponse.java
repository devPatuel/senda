package dev.jordi.senda.transaction;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.common.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String categoryColor,
        TransactionType type,
        BigDecimal amount,
        LocalDate date,
        String description,
        Instant createdAt) {

    /**
     * Must be called within an open transaction: it touches the lazy category.
     */
    public static TransactionResponse from(Transaction transaction) {
        Category category = transaction.getCategory();
        return new TransactionResponse(
                transaction.getId(),
                category.getId(),
                category.getName(),
                category.getColor(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDate(),
                transaction.getDescription(),
                transaction.getCreatedAt());
    }
}
