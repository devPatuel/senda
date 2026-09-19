package dev.jordi.senda.category;

import dev.jordi.senda.common.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a category.
 */
public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull TransactionType type,
        @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$",
                message = "must be a hex color in #RRGGBB format") String color,
        @Size(max = 8) String emoji,
        Long spaceId,
        Boolean fixed,
        Boolean transfer) {
}
