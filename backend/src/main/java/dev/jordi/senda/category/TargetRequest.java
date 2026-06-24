package dev.jordi.senda.category;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Body for POST /api/categories/{id}/target. A null {@code targetAmount} clears
 * the target; otherwise it sets the envelope's funding target (>= 0).
 */
public record TargetRequest(
        @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal targetAmount) {
}
