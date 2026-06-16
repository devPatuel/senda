package dev.jordi.senda.investment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating an asset class.
 */
public record AssetClassRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull PricingSource pricingSource) {
}
