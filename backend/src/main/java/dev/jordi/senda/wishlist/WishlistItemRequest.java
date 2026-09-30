package dev.jordi.senda.wishlist;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WishlistItemRequest(
        @NotBlank @Size(max = 120) String name,
        // http(s) only: these end up in an <img src> and an <a href>, where a
        // javascript: or data: URL would be script the page runs on click
        @Size(max = 1000) @Pattern(regexp = HTTP_URL, message = "must be an http(s) URL") String imageUrl,
        @Size(max = 1000) @Pattern(regexp = HTTP_URL, message = "must be an http(s) URL") String productUrl,
        @Size(max = 1000) String comment,
        @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal price,
        @Min(1) @Max(5) Integer priority
) {
    static final String HTTP_URL = "(?i)^https?://\\S+$";
}
