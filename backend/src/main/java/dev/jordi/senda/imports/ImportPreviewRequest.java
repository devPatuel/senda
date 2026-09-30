package dev.jordi.senda.imports;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Rows to preview, scoped either to the user's personal ledger ({@code spaceId}
 * null) or to a space they belong to (a shared account statement).
 */
public record ImportPreviewRequest(Long spaceId, @NotEmpty @Size(max = ImportService.MAX_ROWS) @Valid List<ImportRowInput> rows) {
}
