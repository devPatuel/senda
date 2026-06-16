package dev.jordi.senda.allocation;

import java.math.BigDecimal;

public record EnvelopeResponse(
        Long id,
        String name,
        BigDecimal percentage,
        int position,
        BigDecimal balance) {

    public static EnvelopeResponse from(AllocationEnvelope envelope, BigDecimal balance) {
        return new EnvelopeResponse(
                envelope.getId(),
                envelope.getName(),
                envelope.getPercentage(),
                envelope.getPosition(),
                balance);
    }
}
