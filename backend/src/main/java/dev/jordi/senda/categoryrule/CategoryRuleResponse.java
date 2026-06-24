package dev.jordi.senda.categoryrule;

/**
 * A category rule with its category's name/color for display.
 */
public record CategoryRuleResponse(
        Long id,
        String matchText,
        Long categoryId,
        String categoryName,
        String categoryColor) {
}
