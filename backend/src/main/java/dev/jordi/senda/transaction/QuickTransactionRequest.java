package dev.jordi.senda.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Lightweight body for POST /api/transactions/quick (Apple Shortcut capture).
 * Type is always EXPENSE and date is today.
 */
public record QuickTransactionRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank String description,
        @NotNull Long categoryId) {
}
