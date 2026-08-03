package dev.jordi.senda.habit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Field-level validation only (400). Cross-field coherence lives in the service (422). */
public record HabitRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 8) String emoji,
        @NotNull HabitType type,
        BigDecimal target,
        @Size(max = 20) String unit,
        @NotNull ScheduleType scheduleType,
        String weekdays,
        Integer intervalDays,
        Integer weeklyTarget,
        Integer sortOrder) {
}
