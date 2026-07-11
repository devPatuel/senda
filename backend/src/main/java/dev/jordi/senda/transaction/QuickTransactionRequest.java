package dev.jordi.senda.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Lightweight body for POST /api/transactions/quick (Apple Shortcut capture).
 * Type is always EXPENSE and date is today; when {@code categoryId} is null the
 * category is resolved from the user's category rules against {@code description}.
 */
public record QuickTransactionRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank String description,
        Long categoryId) {
}
