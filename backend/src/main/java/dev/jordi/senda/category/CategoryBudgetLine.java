package dev.jordi.senda.category;

import java.math.BigDecimal;

/**
 * One expense category within the budget view: its envelope balance, how much
 * has been spent this calendar month, its target percentage in the plan (null
 * when the category is not part of the salary split), and its optional funding
 * target amount (null when no target is set).
 */
public record CategoryBudgetLine(
        Long id,
        String name,
        String color,
        BigDecimal balance,
        BigDecimal spentThisMonth,
        BigDecimal targetPercentage,
        BigDecimal targetAmount) {
}
