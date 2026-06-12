package dev.jordi.senda.transaction;

import java.util.List;

/**
 * Stable pagination envelope; Spring's Page is not serialized directly
 * because its JSON shape is not part of the public API contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
