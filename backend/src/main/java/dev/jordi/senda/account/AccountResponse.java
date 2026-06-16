package dev.jordi.senda.account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String name,
        AccountType type,
        BigDecimal balance,
        String currency,
        boolean archived,
        Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getName(), account.getType(),
                account.getBalance(), account.getCurrency(), account.isArchived(), account.getCreatedAt());
    }
}
