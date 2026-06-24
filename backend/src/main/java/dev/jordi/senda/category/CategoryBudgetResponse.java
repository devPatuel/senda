package dev.jordi.senda.category;

import java.math.BigDecimal;
import java.util.List;

/**
 * Budget overview. The invariant {@code toAssign = totalAccounts - totalAssigned}
 * holds by construction, so "to assign" may be negative when the envelopes hold
 * more than the real money in accounts.
 */
public record CategoryBudgetResponse(
        BigDecimal totalAccounts,
        BigDecimal totalAssigned,
        BigDecimal toAssign,
        List<CategoryBudgetLine> categories) {
}
