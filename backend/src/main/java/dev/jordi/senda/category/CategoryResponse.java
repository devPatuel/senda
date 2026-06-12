package dev.jordi.senda.category;

import dev.jordi.senda.common.TransactionType;

public record CategoryResponse(
        Long id,
        String name,
        TransactionType type,
        String color,
        boolean active) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType(),
                category.getColor(), category.isActive());
    }
}
