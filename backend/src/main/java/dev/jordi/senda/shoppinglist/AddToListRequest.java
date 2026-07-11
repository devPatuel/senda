package dev.jordi.senda.shoppinglist;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddToListRequest(
        @NotNull Long productId,
        @Min(1) Integer quantity   // nullable -> defaults to 1 in the service
) {}
