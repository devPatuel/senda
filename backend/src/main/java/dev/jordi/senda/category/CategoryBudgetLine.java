package dev.jordi.senda.category;

import java.math.BigDecimal;

/**
 * One expense category within the budget view: its envelope balance, how much
 * has been spent this calendar month, and its target percentage in the plan
 * (null when the category is not part of the salary split).
 */
public record CategoryBudgetLine(
        Long id,
        String name,
        String color,
        BigDecimal balance,
        BigDecimal spentThisMonth,
        BigDecimal targetPercentage) {
}
