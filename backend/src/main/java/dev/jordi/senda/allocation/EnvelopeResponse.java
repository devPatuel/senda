package dev.jordi.senda.allocation;

import java.math.BigDecimal;

/**
 * An expense category as seen by the allocation (salary-split) view: its target
 * percentage in the plan ({@code 0} when not assigned) and its current envelope
 * balance. {@code id} is the category id.
 */
public record EnvelopeResponse(
        Long id,
        String name,
        String color,
        BigDecimal percentage,
        BigDecimal balance) {
}
