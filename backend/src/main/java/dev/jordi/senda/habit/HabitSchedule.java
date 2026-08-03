package dev.jordi.senda.habit;

import java.time.LocalDate;

/**
 * Decides whether a habit is due on a given day. Pure and stateless: everything it
 * needs to know about the world arrives in {@link ScheduleContext}, which is what
 * makes it testable with fixed dates and no database.
 */
public final class HabitSchedule {

    private HabitSchedule() { }

    /**
     * @param lastCompleted     last day the habit was completed, or null if never
     * @param completedThisWeek completions within the ISO week of the day being asked about
     */
    public record ScheduleContext(LocalDate lastCompleted, int completedThisWeek) { }

    public static boolean isDueOn(Habit habit, LocalDate date, ScheduleContext ctx) {
        return switch (habit.getScheduleType()) {
            case WEEKDAYS -> isWeekdayScheduled(habit.getWeekdays(), date);
            case INTERVAL -> ctx.lastCompleted() == null
                    || !date.isBefore(ctx.lastCompleted().plusDays(habit.getIntervalDays()));
            // No fixed day: it stays due every day until the weekly target is met.
            case WEEKLY_COUNT -> ctx.completedThisWeek() < habit.getWeeklyTarget();
        };
    }

    /** The mask is seven characters starting on Monday: "1010100" is Mon/Wed/Fri. */
    public static boolean isWeekdayScheduled(String weekdays, LocalDate date) {
        return weekdays.charAt(date.getDayOfWeek().getValue() - 1) == '1';
    }
}
