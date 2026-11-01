package dev.jordi.senda.account;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for updating an account.
 *
 * <p>{@code archived} is optional: when {@code null}, the current value is kept.
 * Sending {@code archived: true} archives the account; {@code false} restores it.
 *
 * <p>{@code balance} is optional too, and is sent only when the person typed a new
 * one. Movements change the balance on their own, so a page loaded earlier that sent
 * back the figure it was showing would silently undo them.
 */
public record AccountUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AccountType type,
        @Digits(integer = 12, fraction = 2) BigDecimal balance,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code") String currency,
        Boolean archived) {
}
