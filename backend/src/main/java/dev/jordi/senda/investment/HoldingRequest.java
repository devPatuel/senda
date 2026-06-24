package dev.jordi.senda.investment;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for creating a holding (position). Initial {@code quantity} and
 * {@code avgCost} may be 0 (an empty position, later filled with buys), or set
 * to seed a starting balance. To add to an existing position use a buy instead.
 */
public record HoldingRequest(
        @NotNull Long assetClassId,
        // Alphanumeric only: the symbol can flow into an outbound CoinGecko query,
        // so reject anything that is not a plausible ticker/ISIN as defense in depth.
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9]+",
                message = "must be alphanumeric") String symbol,
        @NotBlank @Size(max = 100) String name,
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 8) BigDecimal quantity,
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 8) BigDecimal avgCost) {
}
