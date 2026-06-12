package dev.jordi.senda.transaction;

import java.math.BigDecimal;
import java.util.List;

public record MonthlySummaryResponse(
        int year,
        int month,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,
        List<CategorySummary> byCategory) {
}
