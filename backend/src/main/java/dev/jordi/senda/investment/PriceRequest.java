package dev.jordi.senda.investment;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Request body for setting a holding's current price by hand (METAL / FUND /
 * MANUAL sources, or a manual override). Setting it stamps {@code lastPricedAt}.
 */
public record PriceRequest(
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 8) BigDecimal currentPrice) {
}
