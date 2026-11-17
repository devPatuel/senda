package dev.jordi.senda.investment;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for selling part (or all) of a holding. The sale price is kept in the
 * lot history; it does not change the average cost of what remains.
 */
public record SellRequest(
        @NotNull @Positive @Digits(integer = 12, fraction = 8) BigDecimal quantity,
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 8) BigDecimal unitPrice,
        @NotNull LocalDate date) {
}
