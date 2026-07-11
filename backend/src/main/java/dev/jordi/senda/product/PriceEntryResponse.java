package dev.jordi.senda.product;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceEntryResponse(
        Long id,
        BigDecimal price,
        String supermarket,
        Instant recordedAt
) {}
