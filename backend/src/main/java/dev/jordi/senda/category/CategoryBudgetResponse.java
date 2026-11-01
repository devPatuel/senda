package dev.jordi.senda.category;

import java.math.BigDecimal;
import java.util.List;

/**
 * Budget overview. The invariant is {@code toAssign = totalAccounts -
 * totalAvailable}: what is held in the envelopes is what is still LEFT in them,
 * not what was once put in, because spent money has already left both the
 * envelope and the account. Using {@code totalAssigned} here instead would
 * subtract every expense twice — once from the account balance when the
 * statement is imported, and again as money still sitting in a category.
 *
 * <p>{@code toAssign} may be negative when the envelopes hold more than the real
 * money in accounts. It is larger than {@code totalAccounts} when some category
 * is overspent: {@code overspent} is that debt, money that has to be assigned to
 * bring the red categories back to zero.
 *
 * <p>{@code realAccounts} is the total balance of the accounts, {@code
 * expectedAccounts} what the movements say it should be, and {@code discrepancy}
 * = realAccounts - expectedAccounts: a gap means a movement is missing or wrong.
 * In a space the expected figure is {@code totalAccounts}, derived from movements.
 * In the personal budget {@code totalAccounts} stays the real balance, so "to
 * assign" keeps its meaning, and the expected figure is the starting point plus
 * the net of the movements typed since. All three are null when there is nothing
 * to compare: a space without accounts, or a personal budget with no starting point.
 */
public record CategoryBudgetResponse(
        BigDecimal totalAccounts,
        BigDecimal totalAssigned,
        BigDecimal totalAvailable,
        BigDecimal overspent,
        BigDecimal toAssign,
        BigDecimal realAccounts,
        BigDecimal expectedAccounts,
        BigDecimal discrepancy,
        List<CategoryBudgetLine> categories) {
}
