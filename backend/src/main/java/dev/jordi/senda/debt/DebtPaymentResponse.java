package dev.jordi.senda.debt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DebtPaymentResponse(
        Long id,
        Long debtId,
        BigDecimal amount,
        LocalDate date,
        String note,
        Instant createdAt) {

    public static DebtPaymentResponse from(DebtPayment payment) {
        return new DebtPaymentResponse(
                payment.getId(),
                payment.getDebtId(),
                payment.getAmount(),
                payment.getDate(),
                payment.getNote(),
                payment.getCreatedAt());
    }
}
