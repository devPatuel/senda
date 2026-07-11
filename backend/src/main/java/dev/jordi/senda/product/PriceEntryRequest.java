package dev.jordi.senda.product;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PriceEntryRequest(
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal price,
        @NotBlank @Size(max = 80) String supermarket
) {}
