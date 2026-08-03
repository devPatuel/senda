package dev.jordi.senda.habit;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HabitEntryResponse(LocalDate date, BigDecimal value, boolean done) {

    public static HabitEntryResponse from(HabitEntry entry) {
        return new HabitEntryResponse(entry.getEntryDate(), entry.getValue(), entry.isDone());
    }
}
