package dev.jordi.senda.investment;

import java.time.Instant;

public record AssetClassResponse(
        Long id,
        String name,
        PricingSource pricingSource,
        Instant createdAt) {

    public static AssetClassResponse from(AssetClass assetClass) {
        return new AssetClassResponse(assetClass.getId(), assetClass.getName(),
                assetClass.getPricingSource(), assetClass.getCreatedAt());
    }
}
