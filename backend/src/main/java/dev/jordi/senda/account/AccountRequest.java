package dev.jordi.senda.account;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for creating an account. {@code balance} may be negative
 * (e.g. a bank account in overdraft). {@code currency} is optional: when
 * blank it defaults to EUR in the service.
 */
public record AccountRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AccountType type,
        @NotNull @Digits(integer = 12, fraction = 2) BigDecimal balance,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code") String currency) {
}
