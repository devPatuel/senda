package dev.jordi.senda.transaction;

import dev.jordi.senda.common.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull Long categoryId,
        @NotNull TransactionType type,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull LocalDate date,
        @Size(max = 500) String description,
        Long spaceId) {
}
