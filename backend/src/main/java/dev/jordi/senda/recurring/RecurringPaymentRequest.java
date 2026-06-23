package dev.jordi.senda.recurring;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body for create/update of a recurring payment. Frequency-specific fields are
 * validated in the service: {@code month} is required for ANNUAL and QUARTERLY,
 * {@code dayOfWeek} for WEEKLY. {@code dayOfMonth} is always sent (weekly payments
 * pass a placeholder that the forecast ignores). {@code endDate} is an optional
 * cancellation reminder.
 */
public record RecurringPaymentRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull RecurringFrequency frequency,
        @NotNull Long categoryId,
        @NotNull @Min(1) @Max(31) Integer dayOfMonth,
        @Min(1) @Max(12) Integer month,
        @Min(1) @Max(7) Integer dayOfWeek,
        LocalDate endDate) {
}
