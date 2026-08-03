package dev.jordi.senda.habit;

import dev.jordi.senda.habit.HabitSchedule.ScheduleContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HabitScheduleTest {

    // 2026-08-03 is a Monday.
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 3);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);

    private Habit weekdays(String mask) {
        return new Habit(1L, "Leer", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, mask, null, null, 0);
    }

    private Habit every(int days) {
        return new Habit(1L, "Afeitarme", null, HabitType.CHECK, null, null,
                ScheduleType.INTERVAL, null, days, null, 0);
    }

    private Habit timesPerWeek(int times) {
        return new Habit(1L, "Gym", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKLY_COUNT, null, null, times, 0);
    }

    private static final ScheduleContext NEVER = new ScheduleContext(null, 0);

    @Test
    void weekdays_dueOnlyOnTheMarkedDays() {
        Habit monWedFri = weekdays("1010100");

        assertThat(HabitSchedule.isDueOn(monWedFri, MONDAY, NEVER)).isTrue();
        assertThat(HabitSchedule.isDueOn(monWedFri, TUESDAY, NEVER)).isFalse();
        assertThat(HabitSchedule.isDueOn(monWedFri, MONDAY.plusDays(2), NEVER)).isTrue();  // Wednesday
        assertThat(HabitSchedule.isDueOn(monWedFri, MONDAY.plusDays(6), NEVER)).isFalse(); // Sunday
    }

    @Test
    void weekdays_maskStartsOnMonday() {
        assertThat(HabitSchedule.isWeekdayScheduled("1000000", MONDAY)).isTrue();
        assertThat(HabitSchedule.isWeekdayScheduled("0000001", MONDAY.plusDays(6))).isTrue();
        assertThat(HabitSchedule.isWeekdayScheduled("0000001", MONDAY)).isFalse();
    }

    @Test
    void interval_dueWhenNeverDone() {
        assertThat(HabitSchedule.isDueOn(every(2), MONDAY, NEVER)).isTrue();
    }

    @Test
    void interval_countsFromTheLastCompletion_notFromAFixedGrid() {
        Habit everyTwoDays = every(2);
        ScheduleContext doneMonday = new ScheduleContext(MONDAY, 0);

        assertThat(HabitSchedule.isDueOn(everyTwoDays, TUESDAY, doneMonday)).isFalse();
        assertThat(HabitSchedule.isDueOn(everyTwoDays, MONDAY.plusDays(2), doneMonday)).isTrue();

        // Skipped Wednesday and did it on Thursday: the next one is Saturday, not Friday.
        ScheduleContext doneThursday = new ScheduleContext(MONDAY.plusDays(3), 0);
        assertThat(HabitSchedule.isDueOn(everyTwoDays, MONDAY.plusDays(4), doneThursday)).isFalse();
        assertThat(HabitSchedule.isDueOn(everyTwoDays, MONDAY.plusDays(5), doneThursday)).isTrue();
    }

    @Test
    void weeklyCount_dueAnyDayUntilTheWeeklyTargetIsMet() {
        Habit threeTimes = timesPerWeek(3);

        assertThat(HabitSchedule.isDueOn(threeTimes, TUESDAY, new ScheduleContext(MONDAY, 2))).isTrue();
        assertThat(HabitSchedule.isDueOn(threeTimes, TUESDAY, new ScheduleContext(MONDAY, 3))).isFalse();
    }
}
