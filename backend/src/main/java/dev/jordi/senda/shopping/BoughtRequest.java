package dev.jordi.senda.shopping;

import jakarta.validation.constraints.NotNull;

public record BoughtRequest(
        @NotNull Boolean bought
) {}
