package dev.jordi.senda.habit;

import dev.jordi.senda.habit.StreakCalculator.Streak;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreakCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    // Monday
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 3);

    // The creation date is fixed on purpose: with onCreate() it would be the real
    // "now" of the test run, and every case here is anchored to 2026-08-03.
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    private Habit daily() {
        Habit habit = new Habit(1L, "Leer", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, "1111111", null, null, 0);
        habit.setCreatedAt(CREATED);
        return habit;
    }

    private List<HabitEntry> doneOn(LocalDate... days) {
        List<HabitEntry> entries = new ArrayList<>();
        for (LocalDate day : days) entries.add(new HabitEntry(1L, 1L, day, null, true));
        return entries;
    }

    @Test
    void dailyHabit_countsConsecutiveDaysBackFromYesterday() {
        List<HabitEntry> entries = doneOn(TODAY.minusDays(3), TODAY.minusDays(2), TODAY.minusDays(1));

        assertThat(StreakCalculator.calculate(daily(), entries, TODAY, ZONE).current()).isEqualTo(3);
    }

    @Test
    void todayPendingDoesNotBreakTheStreak() {
        // Nothing recorded today and it is 09:00: that is not a failure yet.
        List<HabitEntry> entries = doneOn(TODAY.minusDays(1), TODAY.minusDays(2));

        assertThat(StreakCalculator.calculate(daily(), entries, TODAY, ZONE).current()).isEqualTo(2);
    }

    @Test
    void todayDoneAddsToTheStreak() {
        List<HabitEntry> entries = doneOn(TODAY.minusDays(1), TODAY);

        assertThat(StreakCalculator.calculate(daily(), entries, TODAY, ZONE).current()).isEqualTo(2);
    }

    @Test
    void aMissedScheduledDayBreaksIt() {
        List<HabitEntry> entries = doneOn(TODAY.minusDays(4), TODAY.minusDays(1));

        assertThat(StreakCalculator.calculate(daily(), entries, TODAY, ZONE).current()).isEqualTo(1);
    }

    @Test
    void unscheduledDaysNeitherAddNorBreak() {
        Habit monWedFri = new Habit(1L, "Gym", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, "1010100", null, null, 0);
        monWedFri.setCreatedAt(CREATED);
        // Previous Mon, Wed and Fri done; the weekend in between is not scheduled.
        List<HabitEntry> entries = doneOn(TODAY.minusDays(7), TODAY.minusDays(5), TODAY.minusDays(3));

        assertThat(StreakCalculator.calculate(monWedFri, entries, TODAY, ZONE).current()).isEqualTo(3);
    }

    @Test
    void bestStreakIsTheLongestEverAchieved() {
        List<HabitEntry> entries = doneOn(
                TODAY.minusDays(9), TODAY.minusDays(8), TODAY.minusDays(7), TODAY.minusDays(6),
                TODAY.minusDays(1));

        Streak streak = StreakCalculator.calculate(daily(), entries, TODAY, ZONE);
        assertThat(streak.current()).isEqualTo(1);
        assertThat(streak.best()).isEqualTo(4);
    }

    @Test
    void theStreakDoesNotCrossAScheduleChange() {
        Habit habit = daily();
        habit.setScheduleChangedAt(TODAY.minusDays(2));
        List<HabitEntry> entries = doneOn(TODAY.minusDays(4), TODAY.minusDays(3),
                TODAY.minusDays(2), TODAY.minusDays(1));

        // Only the two days from the change onwards count.
        assertThat(StreakCalculator.calculate(habit, entries, TODAY, ZONE).current()).isEqualTo(2);
    }

    @Test
    void nothingIsJudgedBeforeTheHabitExisted() {
        Habit habit = daily();
        // Created yesterday: the empty days before it must not break anything.
        habit.setCreatedAt(Instant.parse("2026-08-02T08:00:00Z"));
        List<HabitEntry> entries = doneOn(TODAY.minusDays(1));

        assertThat(StreakCalculator.calculate(habit, entries, TODAY, ZONE).current()).isEqualTo(1);
    }

    @Test
    void weeklyCount_countsWeeksNotDays() {
        Habit threeTimes = new Habit(1L, "Gym", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKLY_COUNT, null, null, 3, 0);
        threeTimes.setCreatedAt(CREATED);
        // Two full weeks with three sessions each, plus one session this week (still open).
        List<HabitEntry> entries = doneOn(
                TODAY.minusDays(14), TODAY.minusDays(13), TODAY.minusDays(12),
                TODAY.minusDays(7), TODAY.minusDays(6), TODAY.minusDays(5),
                TODAY);

        assertThat(StreakCalculator.calculate(threeTimes, entries, TODAY, ZONE).current()).isEqualTo(2);
    }

    @Test
    void interval_breaksWhenTheGapExceedsTheInterval() {
        Habit everyTwoDays = new Habit(1L, "Afeitarme", null, HabitType.CHECK, null, null,
                ScheduleType.INTERVAL, null, 2, null, 0);
        everyTwoDays.setCreatedAt(CREATED);
        List<HabitEntry> ok = doneOn(TODAY.minusDays(4), TODAY.minusDays(2));
        List<HabitEntry> broken = doneOn(TODAY.minusDays(9), TODAY.minusDays(2));

        assertThat(StreakCalculator.calculate(everyTwoDays, ok, TODAY, ZONE).current()).isEqualTo(2);
        assertThat(StreakCalculator.calculate(everyTwoDays, broken, TODAY, ZONE).current()).isEqualTo(1);
    }
}
