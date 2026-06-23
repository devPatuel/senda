package dev.jordi.senda.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A recurring payment with its derived next due date and monthly-equivalent cost
 * (annual amounts divided by 12), plus its category name/color for display.
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
        LocalDate nextDueDate,
        BigDecimal monthlyEquivalent) {
}
