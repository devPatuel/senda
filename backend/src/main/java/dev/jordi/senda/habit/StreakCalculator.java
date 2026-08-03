package dev.jordi.senda.habit;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Current and best streak for a habit. Pure: it receives the entries and the day,
 * and never reads the clock or the database.
 *
 * Three rules from the spec: the day in progress never breaks a streak, nothing is
 * judged before the habit existed, and a streak never crosses a schedule change.
 */
public final class StreakCalculator {

    private StreakCalculator() { }

    public record Streak(int current, int best) { }

    public static Streak calculate(Habit habit, List<HabitEntry> entries, LocalDate today, ZoneId zone) {
        LocalDate floor = floorDate(habit, zone);
        Set<LocalDate> done = entries.stream()
                .filter(HabitEntry::isDone)
                .map(HabitEntry::getEntryDate)
                .filter(date -> !date.isBefore(floor) && !date.isAfter(today))
                .collect(Collectors.toCollection(TreeSet::new));

        if (habit.getScheduleType() == ScheduleType.WEEKLY_COUNT) {
            return weeklyStreak(habit, done, today, floor);
        }
        if (habit.getScheduleType() == ScheduleType.INTERVAL) {
            return intervalStreak(habit, done);
        }
        return weekdayStreak(habit, done, today, floor);
    }

    /** Nothing before the habit existed, and nothing before the schedule was changed. */
    private static LocalDate floorDate(Habit habit, ZoneId zone) {
        LocalDate created = habit.getCreatedOn(zone);
        LocalDate changed = habit.getScheduleChangedAt();
        return changed == null || changed.isBefore(created) ? created : changed;
    }

    private static Streak weekdayStreak(Habit habit, Set<LocalDate> done, LocalDate today, LocalDate floor) {
        int current = 0;
        boolean counting = true;
        int best = 0;
        int run = 0;

        for (LocalDate day = today; !day.isBefore(floor); day = day.minusDays(1)) {
            if (!HabitSchedule.isWeekdayScheduled(habit.getWeekdays(), day)) continue;

            if (done.contains(day)) {
                run++;
                if (counting) current++;
            } else if (day.equals(today)) {
                // The day in progress is not a failure yet: it neither adds nor breaks.
                continue;
            } else {
                best = Math.max(best, run);
                run = 0;
                counting = false;
            }
        }
        return new Streak(current, Math.max(best, run));
    }

    private static Streak intervalStreak(Habit habit, Set<LocalDate> done) {
        List<LocalDate> days = List.copyOf(done);          // ascending: TreeSet
        int best = 0, run = 0;
        int current = 0;

        for (int i = 0; i < days.size(); i++) {
            if (i == 0 || days.get(i - 1).plusDays(habit.getIntervalDays()).isBefore(days.get(i))) {
                best = Math.max(best, run);
                run = 1;
            } else {
                run++;
            }
            current = run;                                  // the last run is the current one
        }
        return new Streak(current, Math.max(best, run));
    }

    private static Streak weeklyStreak(Habit habit, Set<LocalDate> done, LocalDate today, LocalDate floor) {
        WeekFields iso = WeekFields.ISO;
        TreeMap<LocalDate, Integer> perWeek = new TreeMap<>();
        for (LocalDate day : done) {
            LocalDate monday = day.with(iso.dayOfWeek(), 1);
            perWeek.merge(monday, 1, Integer::sum);
        }

        LocalDate thisMonday = today.with(iso.dayOfWeek(), 1);
        LocalDate floorMonday = floor.with(iso.dayOfWeek(), 1);

        int current = 0;
        boolean counting = true;
        int best = 0, run = 0;

        for (LocalDate monday = thisMonday; !monday.isBefore(floorMonday); monday = monday.minusWeeks(1)) {
            boolean met = perWeek.getOrDefault(monday, 0) >= habit.getWeeklyTarget();
            if (met) {
                run++;
                if (counting) current++;
            } else if (monday.equals(thisMonday)) {
                // The week in progress is not lost until it ends.
                continue;
            } else {
                best = Math.max(best, run);
                run = 0;
                counting = false;
            }
        }
        return new Streak(current, Math.max(best, run));
    }
}
