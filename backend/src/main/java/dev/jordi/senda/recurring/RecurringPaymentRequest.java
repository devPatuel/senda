package dev.jordi.senda.recurring;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for create/update of a recurring payment. {@code month} is required only
 * for ANNUAL frequency (validated in the service); ignored for MONTHLY.
 */
public record RecurringPaymentRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull RecurringFrequency frequency,
        @NotNull Long categoryId,
        @NotNull @Min(1) @Max(31) Integer dayOfMonth,
        @Min(1) @Max(12) Integer month) {
}
