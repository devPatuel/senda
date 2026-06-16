package dev.jordi.senda.account;

import java.math.BigDecimal;

/**
 * Total liquid balance across the user's non-archived accounts.
 *
 * <p>Balances are summed regardless of each account's {@code currency}: the app
 * assumes a single base currency (EUR by default). Multi-currency conversion is
 * intentionally out of scope.
 */
public record TotalBalanceResponse(BigDecimal total) {
}
