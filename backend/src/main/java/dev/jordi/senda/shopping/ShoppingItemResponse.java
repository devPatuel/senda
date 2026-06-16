package dev.jordi.senda.shopping;

import java.math.BigDecimal;
import java.time.Instant;

public record ShoppingItemResponse(
        Long id,
        ShoppingListType listType,
        String name,
        BigDecimal estimatedPrice,
        Long envelopeId,
        String envelopeName,
        BigDecimal envelopeBalance,
        Integer priority,
        boolean bought,
        Boolean feasible,
        String notes,
        Instant createdAt
) {}
