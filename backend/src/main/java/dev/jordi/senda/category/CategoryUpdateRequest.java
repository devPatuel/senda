package dev.jordi.senda.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating a category.
 *
 * <p>The category {@code type} is immutable after creation: it is intentionally
 * not part of this DTO, so a {@code "type"} field sent in the JSON body is
 * silently ignored. Changing the type would silently re-classify every existing
 * transaction of the category, which is never what the user wants.
 *
 * <p>{@code active} is optional: when {@code null}, the current value is kept.
 */
public record CategoryUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$",
                message = "must be a hex color in #RRGGBB format") String color,
        @Size(max = 8) String emoji,
        Boolean active) {
}
