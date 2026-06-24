package dev.jordi.senda.investment;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for adding a buy ("lot") to an existing holding. The holding's
 * quantity and weighted-average cost are recalculated from it.
 */
public record BuyRequest(
        @NotNull @Positive @Digits(integer = 12, fraction = 8) BigDecimal quantity,
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 8) BigDecimal unitPrice,
        @NotNull LocalDate date) {
}
