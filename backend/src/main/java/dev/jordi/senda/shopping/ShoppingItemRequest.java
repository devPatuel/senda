package dev.jordi.senda.shopping;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ShoppingItemRequest(
        @NotNull ShoppingListType listType,
        @NotBlank @Size(max = 100) String name,
        @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal estimatedPrice,
        Long envelopeId,
        // Optional 1..5 priority scale (1 = most urgent); bounded so junk values
        // cannot distort the wishlist ordering.
        @Min(1) @Max(5) Integer priority,
        @Size(max = 500) String notes
) {}
