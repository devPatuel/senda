package dev.jordi.senda.category;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body for POST /api/categories/{id}/assign. The amount is a delta added to the
 * envelope balance and may be negative (to pull money back out of a category).
 */
public record AssignRequest(
        @NotNull @Digits(integer = 12, fraction = 2) BigDecimal amount) {
}
