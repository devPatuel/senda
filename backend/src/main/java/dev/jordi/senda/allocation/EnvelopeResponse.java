package dev.jordi.senda.allocation;

import java.math.BigDecimal;

/**
 * An expense category as seen by the allocation (salary-split) view: its target
 * percentage in the plan ({@code 0} when not assigned), what has been assigned
 * to its envelope ({@code balance}), everything ever spent from it
 * ({@code spent}, transfers excluded) and what is left ({@code available},
 * negative when overspent). {@code id} is the category id.
 */
public record EnvelopeResponse(
        Long id,
        String name,
        String color,
        BigDecimal percentage,
        BigDecimal balance,
        BigDecimal spent,
        BigDecimal available) {
}
