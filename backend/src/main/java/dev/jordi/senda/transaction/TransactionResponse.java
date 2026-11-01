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
        String categoryEmoji,
        TransactionType type,
        BigDecimal amount,
        LocalDate date,
        String description,
        Instant createdAt,
        Long accountId,
        BigDecimal accountBalance) {

    /**
     * Must be called within an open transaction: it touches the lazy category.
     */
    public static TransactionResponse from(Transaction transaction) {
        return from(transaction, null);
    }

    /**
     * @param accountBalance the account's balance right after this write; null when
     *                       nothing was charged, or on reads, where it would be stale
     */
    public static TransactionResponse from(Transaction transaction, BigDecimal accountBalance) {
        Category category = transaction.getCategory();
        return new TransactionResponse(
                transaction.getId(),
                category.getId(),
                category.getName(),
                category.getColor(),
                category.getEmoji(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDate(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                transaction.getAccountId(),
                accountBalance);
    }
}
