package dev.jordi.senda.transaction;

import java.math.BigDecimal;
import java.util.List;

/**
 * A whole calendar year at a glance: totals, the twelve months in order (dense,
 * so a chart never has to guess at gaps) and the per-category breakdown.
 *
 * <p>Transfers are left out of every figure, exactly as in the monthly summary:
 * moving your own money between accounts is not income or spending, and counting
 * it would inflate the totals and every average derived from them.
 */
public record YearSummaryResponse(
        int year,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,
        BigDecimal monthlyAverageExpense,
        List<MonthlyTrend> months,
        List<CategorySummary> byCategory) {
}
