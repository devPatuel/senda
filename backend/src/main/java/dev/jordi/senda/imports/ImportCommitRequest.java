package dev.jordi.senda.imports;

import dev.jordi.senda.common.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ImportCommitRequest(Long spaceId, @NotEmpty @Valid List<Row> rows) {

    /**
     * One confirmed row to import: a positive amount, an explicit type and the
     * category the user picked (possibly pre-filled from a rule).
     */
    public record Row(
            @NotNull LocalDate date,
            @Size(max = 500) String description,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull TransactionType type,
            @NotNull Long categoryId) {
    }
}
