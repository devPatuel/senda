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
 */
public record CategoryBudgetResponse(
        BigDecimal totalAccounts,
        BigDecimal totalAssigned,
        BigDecimal totalAvailable,
        BigDecimal overspent,
        BigDecimal toAssign,
        List<CategoryBudgetLine> categories) {
}
