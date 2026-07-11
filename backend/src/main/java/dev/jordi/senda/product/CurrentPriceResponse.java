package dev.jordi.senda.product;

import java.math.BigDecimal;
import java.time.Instant;

public record CurrentPriceResponse(
        String supermarket,
        BigDecimal price,
        Instant recordedAt
) {}
