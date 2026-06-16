package dev.jordi.senda.allocation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A single envelope within a plan save request. {@code id} is optional:
 * when present the existing envelope is updated in-place, preserving its
 * accumulated balance. A null {@code id} creates a new envelope.
 */
public record EnvelopeLineRequest(
        Long id,
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal percentage) {
}
