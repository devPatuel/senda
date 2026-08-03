package dev.jordi.senda.habit;

import java.math.BigDecimal;

/** For CHECK habits the value is ignored; for COUNTER and MEASURE it is the number recorded. */
public record HabitEntryRequest(BigDecimal value) { }
