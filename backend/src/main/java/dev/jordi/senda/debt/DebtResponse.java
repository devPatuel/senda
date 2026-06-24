package dev.jordi.senda.debt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DebtResponse(
        Long id,
        DebtDirection direction,
        String counterparty,
        String concept,
        BigDecimal originalAmount,
        BigDecimal paidAmount,
        BigDecimal pendingAmount,
        boolean settled,
        LocalDate date,
        Instant createdAt) {

    /**
     * @param debt the managed entity
     * @param paid aggregated sum of payments (from DB, never null)
     */
    public static DebtResponse from(Debt debt, BigDecimal paid) {
        BigDecimal pending = debt.getOriginalAmount().subtract(paid);
        return new DebtResponse(
                debt.getId(),
                debt.getDirection(),
                debt.getCounterparty(),
                debt.getConcept(),
                debt.getOriginalAmount(),
                paid,
                pending,
                debt.isSettled(),
                debt.getDate(),
                debt.getCreatedAt());
    }
}
