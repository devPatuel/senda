package dev.jordi.senda.categoryrule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for create/update of a category rule.
 */
public record CategoryRuleRequest(
        @NotBlank @Size(max = 100) String matchText,
        @NotNull Long categoryId) {
}
