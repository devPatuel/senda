package dev.jordi.senda.habit;

import dev.jordi.senda.common.ClockConfig;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.habit.HabitSchedule.ScheduleContext;
import dev.jordi.senda.habit.StreakCalculator.Streak;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Composed reads: the today view and the history of one habit. */
@Service
public class HabitQueryService {

    private final HabitRepository habits;
    private final HabitEntryRepository entries;
    private final Clock clock;

    public HabitQueryService(HabitRepository habits, HabitEntryRepository entries, Clock clock) {
        this.habits = habits;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TodayHabitResponse> today(Long userId) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, HabitEntry> todayEntries = entries.findByUserIdAndEntryDate(userId, today).stream()
                .collect(Collectors.toMap(HabitEntry::getHabitId, Function.identity(), (a, b) -> a));

        List<TodayHabitResponse> result = new ArrayList<>();
        for (Habit habit : habits.findByUserIdAndActiveTrueOrderBySortOrderAscIdAsc(userId)) {
            List<HabitEntry> all = entries.findByHabitIdOrderByEntryDateAsc(habit.getId());
            HabitEntry entry = todayEntries.get(habit.getId());

            // An already recorded habit stays on the list even if it is no longer due:
            // hiding what you just ticked reads as if the tap had been lost.
            if (!HabitSchedule.isDueOn(habit, today, contextFor(all, today)) && entry == null) {
                continue;
            }
            Streak streak = StreakCalculator.calculate(habit, all, today, ClockConfig.ZONE);
            result.add(new TodayHabitResponse(habit.getId(), habit.getName(), habit.getEmoji(),
                    habit.getType(), habit.getTarget(), habit.getUnit(),
                    entry == null ? null : entry.getValue(),
                    entry != null && entry.isDone(), streak.current()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public HabitHistoryResponse history(Long userId, Long habitId, LocalDate from, LocalDate to) {
        Habit habit = habits.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new NotFoundException("Habit not found"));
        LocalDate today = LocalDate.now(clock);

        List<HabitEntryResponse> window =
                entries.findByHabitIdAndEntryDateBetweenOrderByEntryDateAsc(habitId, from, to).stream()
                        .map(HabitEntryResponse::from)
                        .toList();

        List<HabitEntry> all = entries.findByHabitIdOrderByEntryDateAsc(habitId);
        Streak streak = StreakCalculator.calculate(habit, all, today, ClockConfig.ZONE);

        return new HabitHistoryResponse(HabitResponse.from(habit), window,
                streak.current(), streak.best(), completionRate(habit, all, from, to, today));
    }

    /** Context for the schedule: last completion and completions in the ISO week of `day`. */
    private ScheduleContext contextFor(List<HabitEntry> all, LocalDate day) {
        LocalDate monday = day.with(WeekFields.ISO.dayOfWeek(), 1);
        LocalDate lastCompleted = all.stream()
                .filter(HabitEntry::isDone)
                .map(HabitEntry::getEntryDate)
                .filter(date -> date.isBefore(day))
                .max(LocalDate::compareTo)
                .orElse(null);
        int thisWeek = (int) all.stream()
                .filter(HabitEntry::isDone)
                .map(HabitEntry::getEntryDate)
                .filter(date -> !date.isBefore(monday) && !date.isAfter(day))
                .count();
        return new ScheduleContext(lastCompleted, thisWeek);
    }

    /**
     * Percentage of already-closed scheduled days that were completed. Today is excluded:
     * counting a day still in progress as a failure would drag the number down all morning.
     */
    private int completionRate(Habit habit, List<HabitEntry> all, LocalDate from, LocalDate to,
                               LocalDate today) {
        LocalDate last = to.isBefore(today) ? to : today.minusDays(1);
        if (last.isBefore(from)) return 0;

        List<LocalDate> completed = all.stream().filter(HabitEntry::isDone)
                .map(HabitEntry::getEntryDate).toList();

        int scheduled = 0, done = 0;
        for (LocalDate day = from; !day.isAfter(last); day = day.plusDays(1)) {
            boolean isDue = habit.getScheduleType() == ScheduleType.WEEKDAYS
                    ? HabitSchedule.isWeekdayScheduled(habit.getWeekdays(), day)
                    : HabitSchedule.isDueOn(habit, day, contextFor(all, day));
            if (!isDue) continue;
            scheduled++;
            if (completed.contains(day)) done++;
        }
        return scheduled == 0 ? 0 : Math.round(done * 100f / scheduled);
    }
}
