package dev.jordi.senda.allocation;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Body for POST /api/allocation/distribute.
 * When {@code persist} is {@code true} the allocated amounts are accumulated
 * into each envelope's balance; otherwise the call is a pure simulation.
 */
public record DistributeRequest(
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount,
        boolean persist) {
}
