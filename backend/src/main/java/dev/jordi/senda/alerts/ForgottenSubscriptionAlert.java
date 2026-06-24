package dev.jordi.senda.alerts;

/**
 * A recurring payment whose category has had no expense in the last two months —
 * a subscription you keep paying but no longer seem to use.
 */
public record ForgottenSubscriptionAlert(
        Long recurringId,
        String name,
        Long categoryId,
        String categoryName,
        String categoryColor) {
}
