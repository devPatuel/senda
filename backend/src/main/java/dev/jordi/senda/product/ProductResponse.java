package dev.jordi.senda.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        UnitType unitType,
        BigDecimal amount,
        String unit,
        Instant createdAt,
        // Latest price per supermarket, cheapest first (the comparison view).
        List<CurrentPriceResponse> currentPrices
) {}
