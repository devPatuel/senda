package dev.jordi.senda.habit;

import java.math.BigDecimal;

/** One row of the "today" view: the habit plus its state for the current day. */
public record TodayHabitResponse(Long id, String name, String emoji, HabitType type,
                                 BigDecimal target, String unit, BigDecimal value,
                                 boolean done, int currentStreak) { }
