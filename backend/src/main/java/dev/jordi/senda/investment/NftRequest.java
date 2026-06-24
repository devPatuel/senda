package dev.jordi.senda.investment;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for creating or updating an NFT. {@code buyCryptoAmount} is how
 * much crypto was paid; {@code fiatValueAtPurchase} is its fiat value back then;
 * {@code ourCurrentValue} is the owner's own estimate of what it is worth now.
 */
public record NftRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String collection,
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9]+",
                message = "must be alphanumeric") String buyCryptoSymbol,
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 8) BigDecimal buyCryptoAmount,
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal fiatValueAtPurchase,
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal ourCurrentValue,
        @Size(max = 500) String utility) {
}
