package dev.jordi.senda.shoppinglist;

import jakarta.validation.constraints.Min;

public record UpdateListItemRequest(
        @Min(1) Integer quantity,
        Boolean checked
) {}
