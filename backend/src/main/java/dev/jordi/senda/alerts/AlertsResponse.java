package dev.jordi.senda.alerts;

import java.util.List;

/**
 * Heuristic spending alerts shown on the home screen.
 */
public record AlertsResponse(
        List<AntExpenseAlert> antExpenses,
        List<ForgottenSubscriptionAlert> forgottenSubscriptions) {
}
