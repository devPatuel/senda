package dev.jordi.senda.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A recurring payment with its derived next due date and monthly-equivalent cost
 * (weekly ≈ amount*52/12, quarterly = amount/3, annual = amount/12), plus its
 * category name/color for display. {@code dayOfWeek} is set for WEEKLY only;
 * {@code endDate} is the optional cancellation reminder.
 */
public record RecurringPaymentResponse(
        Long id,
        String name,
        BigDecimal amount,
        RecurringFrequency frequency,
        Long categoryId,
        String categoryName,
        String categoryColor,
        int dayOfMonth,
        Integer month,
        Integer dayOfWeek,
        LocalDate nextDueDate,
        BigDecimal monthlyEquivalent,
        LocalDate endDate,
        BigDecimal previousAmount,
        BigDecimal changePct) {
}
