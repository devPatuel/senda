package dev.jordi.senda.imports;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One parsed CSV row sent for preview. {@code amount} is signed: negative is an
 * expense, positive an income.
 */
public record ImportRowInput(
        @NotNull LocalDate date,
        @Size(max = 500) String description,
        @NotNull @Digits(integer = 12, fraction = 2) BigDecimal amount) {
}
