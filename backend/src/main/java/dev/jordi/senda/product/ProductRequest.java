package dev.jordi.senda.product;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull UnitType unitType,
        // WEIGHT (e.g. 0.5 kg) or QUANTITY (e.g. 6 ud): both need a positive amount + unit.
        @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal amount,
        @NotBlank @Size(max = 20) String unit
) {}
