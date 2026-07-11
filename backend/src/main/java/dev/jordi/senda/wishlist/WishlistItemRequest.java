package dev.jordi.senda.wishlist;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WishlistItemRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String imageUrl,
        @Size(max = 1000) String productUrl,
        @Size(max = 1000) String comment,
        @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal price,
        @Min(1) @Max(5) Integer priority
) {}
