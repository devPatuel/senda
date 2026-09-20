package dev.jordi.senda.transaction;

import java.math.BigDecimal;
import java.util.List;

/**
 * A whole calendar year at a glance: what was spent, what was moved in as
 * transfers, and the twelve months in order (dense, so a chart never has to
 * guess at gaps).
 *
 * <p>There is no yearly average on purpose: dividing by twelve counts months
 * with no activity as if they had been cheap, which turns an incomplete year
 * into a figure that reads as real.
 */
public record YearSummaryResponse(
        int year,
        BigDecimal totalExpense,
        BigDecimal totalTransfersIn,
        List<YearMonthTotals> months) {
}
