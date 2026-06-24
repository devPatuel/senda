package dev.jordi.senda.investment;

import java.util.List;

/**
 * Default asset classes copied to every user on registration. The user can
 * create more (e.g. another fund family or a manual class).
 */
public final class DefaultAssetClasses {

    public record Definition(String name, PricingSource pricingSource) {
    }

    public static final List<Definition> ALL = List.of(
            new Definition("Cripto", PricingSource.CRYPTO),
            new Definition("Fondos", PricingSource.FUND),
            new Definition("Oro", PricingSource.METAL),
            new Definition("Plata", PricingSource.METAL));

    private DefaultAssetClasses() {
    }
}
