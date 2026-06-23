package dev.jordi.senda.allocation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * A single line of the allocation plan: an existing expense category and the
 * percentage of the salary split assigned to it. Categories omitted from the
 * plan have their target percentage cleared.
 */
public record EnvelopeLineRequest(
        @NotNull Long categoryId,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal percentage) {
}
