package dev.jordi.senda.debt;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DebtRequest(
        @NotNull DebtDirection direction,
        @NotBlank @Size(max = 100) String counterparty,
        @NotBlank @Size(max = 255) String concept,
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal originalAmount,
        @NotNull LocalDate date) {
}
