package dev.jordi.senda.investment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record HoldingLotResponse(
        Long id,
        BigDecimal quantity,
        BigDecimal unitPrice,
        LocalDate date,
        LotKind kind,
        Instant createdAt) {

    public static HoldingLotResponse from(HoldingLot lot) {
        return new HoldingLotResponse(lot.getId(), lot.getQuantity(), lot.getUnitPrice(),
                lot.getDate(), lot.getKind(), lot.getCreatedAt());
    }
}
