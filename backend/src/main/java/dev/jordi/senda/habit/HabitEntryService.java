package dev.jordi.senda.habit;

import dev.jordi.senda.common.UnprocessableEntityException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

@Service
public class HabitEntryService {

    /** Today plus the six previous days. Wider than this and a streak stops meaning anything. */
    public static final int WINDOW_DAYS = 7;

    private final HabitEntryRepository entries;
    private final HabitService habits;
    private final Clock clock;

    public HabitEntryService(HabitEntryRepository entries, HabitService habits, Clock clock) {
        this.entries = entries;
        this.habits = habits;
        this.clock = clock;
    }

    @Transactional
    public HabitEntryResponse record(Long userId, Long habitId, LocalDate date, HabitEntryRequest req) {
        Habit habit = habits.findOwned(userId, habitId);
        requireInsideWindow(date);
        BigDecimal value = valueFor(habit, req.value());
        return HabitEntryResponse.from(upsert(habit, userId, date, value));
    }

    @Transactional
    public HabitEntryResponse increment(Long userId, Long habitId, LocalDate date, BigDecimal amount) {
        Habit habit = habits.findOwned(userId, habitId);
        requireInsideWindow(date);
        if (habit.getType() != HabitType.COUNTER) {
            throw new UnprocessableEntityException("Only counter habits can be incremented");
        }
        BigDecimal current = entries.findByHabitIdAndEntryDate(habitId, date)
                .map(HabitEntry::getValue)
                .orElse(BigDecimal.ZERO);
        BigDecimal step = amount == null ? BigDecimal.ONE : amount;
        return HabitEntryResponse.from(upsert(habit, userId, date, current.add(step)));
    }

    @Transactional
    public void clear(Long userId, Long habitId, LocalDate date) {
        habits.findOwned(userId, habitId);
        requireInsideWindow(date);
        entries.deleteByHabitIdAndEntryDate(habitId, date);
    }

    private HabitEntry upsert(Habit habit, Long userId, LocalDate date, BigDecimal value) {
        boolean done = isDone(habit, value);
        return entries.findByHabitIdAndEntryDate(habit.getId(), date)
                .map(existing -> {
                    existing.setValue(value);
                    existing.setDone(done);
                    existing.touch();
                    return existing;
                })
                .orElseGet(() -> entries.save(
                        new HabitEntry(habit.getId(), userId, date, value, done)));
    }

    private BigDecimal valueFor(Habit habit, BigDecimal requested) {
        return switch (habit.getType()) {
            case CHECK -> null;                              // the value is meaningless here
            case COUNTER -> requested == null ? BigDecimal.ZERO : requested;
            case MEASURE -> {
                if (requested == null) {
                    throw new UnprocessableEntityException("A measure habit needs a value");
                }
                yield requested;
            }
        };
    }

    /**
     * Stored, never derived at read time: raising a counter's target later must not turn
     * days that were completed back then into failures.
     */
    private boolean isDone(Habit habit, BigDecimal value) {
        return switch (habit.getType()) {
            case CHECK, MEASURE -> true;
            case COUNTER -> value != null && value.compareTo(habit.getTarget()) >= 0;
        };
    }

    private void requireInsideWindow(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today) || date.isBefore(today.minusDays(WINDOW_DAYS - 1))) {
            throw new UnprocessableEntityException(
                    "Date outside the editable window of " + WINDOW_DAYS + " days");
        }
    }
}
