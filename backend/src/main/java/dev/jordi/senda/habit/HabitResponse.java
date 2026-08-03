package dev.jordi.senda.habit;

import java.math.BigDecimal;

public record HabitResponse(Long id, String name, String emoji, HabitType type, BigDecimal target,
                            String unit, ScheduleType scheduleType, String weekdays,
                            Integer intervalDays, Integer weeklyTarget, boolean active, int sortOrder) {

    public static HabitResponse from(Habit habit) {
        return new HabitResponse(habit.getId(), habit.getName(), habit.getEmoji(), habit.getType(),
                habit.getTarget(), habit.getUnit(), habit.getScheduleType(), habit.getWeekdays(),
                habit.getIntervalDays(), habit.getWeeklyTarget(), habit.isActive(), habit.getSortOrder());
    }
}
