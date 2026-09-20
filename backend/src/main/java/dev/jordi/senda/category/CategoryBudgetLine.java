package dev.jordi.senda.category;

import java.math.BigDecimal;

/**
 * One expense category within the budget view.
 *
 * <p>{@code balance} is what has been assigned to the envelope, {@code spent} is
 * everything ever spent from it and {@code available} is the difference — what
 * is really left. {@code available} goes negative when a category is overspent,
 * which is information (how much to move to cover it), not an error.
 * {@code spentThisMonth} is the current calendar month only, for the monthly
 * reading. Transfers count in none of them.
 */
public record CategoryBudgetLine(
        Long id,
        String name,
        String color,
        BigDecimal balance,
        BigDecimal spentThisMonth,
        BigDecimal spent,
        BigDecimal available,
        BigDecimal targetPercentage,
        BigDecimal targetAmount) {
}
