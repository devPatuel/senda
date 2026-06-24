package dev.jordi.senda.imports;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ImportPreviewRequest(@NotEmpty @Valid List<ImportRowInput> rows) {
}
