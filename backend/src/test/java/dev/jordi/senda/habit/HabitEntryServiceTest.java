package dev.jordi.senda.habit;

import dev.jordi.senda.common.UnprocessableEntityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HabitEntryServiceTest {

    private static final Long USER = 1L;
    private static final Long HABIT = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 3);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneId.of("Europe/Madrid"));

    private HabitEntryRepository entries;
    private HabitService habits;
    private HabitEntryService service;

    // The id matters: upsert() looks the entry up by habit id, and a habit built with
    // new Habit(...) has none until it goes through the repository.
    private Habit withId(Habit habit) {
        habit.setId(HABIT);
        return habit;
    }

    private Habit counterHabit() {
        return withId(new Habit(USER, "Agua", "💧", HabitType.COUNTER, new BigDecimal("8"), "vasos",
                ScheduleType.WEEKDAYS, "1111111", null, null, 0));
    }

    private Habit checkHabit() {
        return withId(new Habit(USER, "Meditar", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, "1111111", null, null, 0));
    }

    private Habit measureHabit() {
        return withId(new Habit(USER, "Pesarme", "⚖️", HabitType.MEASURE, null, "kg",
                ScheduleType.WEEKDAYS, "1111111", null, null, 0));
    }

    @BeforeEach
    void setUp() {
        entries = mock(HabitEntryRepository.class);
        habits = mock(HabitService.class);
        service = new HabitEntryService(entries, habits, CLOCK);
        when(entries.save(any(HabitEntry.class))).thenAnswer(call -> call.getArgument(0));
        when(entries.findByHabitIdAndEntryDate(eq(HABIT), any())).thenReturn(Optional.empty());
    }

    @Test
    void counterIsDoneOnlyWhenItReachesTheTarget() {
        when(habits.findOwned(USER, HABIT)).thenReturn(counterHabit());

        HabitEntryResponse partial = service.record(USER, HABIT, TODAY,
                new HabitEntryRequest(new BigDecimal("5")));
        HabitEntryResponse full = service.record(USER, HABIT, TODAY,
                new HabitEntryRequest(new BigDecimal("8")));

        assertThat(partial.done()).isFalse();
        assertThat(partial.value()).isEqualByComparingTo("5");
        assertThat(full.done()).isTrue();
    }

    @Test
    void aCheckHabitIsDoneWithoutAValue() {
        when(habits.findOwned(USER, HABIT)).thenReturn(checkHabit());

        assertThat(service.record(USER, HABIT, TODAY, new HabitEntryRequest(null)).done()).isTrue();
    }

    @Test
    void aMeasureHabitIsDoneJustByRecordingTheNumber() {
        when(habits.findOwned(USER, HABIT)).thenReturn(measureHabit());

        HabitEntryResponse response = service.record(USER, HABIT, TODAY,
                new HabitEntryRequest(new BigDecimal("78.4")));

        assertThat(response.done()).isTrue();
        assertThat(response.value()).isEqualByComparingTo("78.4");
    }

    @Test
    void aMeasureHabitNeedsAValue() {
        when(habits.findOwned(USER, HABIT)).thenReturn(measureHabit());

        assertThatThrownBy(() -> service.record(USER, HABIT, TODAY, new HabitEntryRequest(null)))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void sixDaysAgoIsInsideTheWindow() {
        when(habits.findOwned(USER, HABIT)).thenReturn(checkHabit());

        assertThat(service.record(USER, HABIT, TODAY.minusDays(6), new HabitEntryRequest(null)).done())
                .isTrue();
    }

    @Test
    void sevenDaysAgoIsOutsideTheWindow() {
        when(habits.findOwned(USER, HABIT)).thenReturn(checkHabit());

        assertThatThrownBy(() ->
                service.record(USER, HABIT, TODAY.minusDays(7), new HabitEntryRequest(null)))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("window");
    }

    @Test
    void theFutureIsRejected() {
        when(habits.findOwned(USER, HABIT)).thenReturn(checkHabit());

        assertThatThrownBy(() ->
                service.record(USER, HABIT, TODAY.plusDays(1), new HabitEntryRequest(null)))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void recordingTwiceOverwritesInsteadOfDuplicating() {
        when(habits.findOwned(USER, HABIT)).thenReturn(counterHabit());
        HabitEntry existing = new HabitEntry(HABIT, USER, TODAY, new BigDecimal("3"), false);
        when(entries.findByHabitIdAndEntryDate(HABIT, TODAY)).thenReturn(Optional.of(existing));

        HabitEntryResponse response = service.record(USER, HABIT, TODAY,
                new HabitEntryRequest(new BigDecimal("6")));

        assertThat(response.value()).isEqualByComparingTo("6");
        assertThat(existing.getValue()).isEqualByComparingTo("6");
    }

    @Test
    void incrementAddsToWhatIsAlreadyThere() {
        when(habits.findOwned(USER, HABIT)).thenReturn(counterHabit());
        HabitEntry existing = new HabitEntry(HABIT, USER, TODAY, new BigDecimal("5"), false);
        when(entries.findByHabitIdAndEntryDate(HABIT, TODAY)).thenReturn(Optional.of(existing));

        HabitEntryResponse response = service.increment(USER, HABIT, TODAY, BigDecimal.ONE);

        assertThat(response.value()).isEqualByComparingTo("6");
    }

    @Test
    void incrementStartsFromZeroWhenThereIsNothingYet() {
        when(habits.findOwned(USER, HABIT)).thenReturn(counterHabit());

        assertThat(service.increment(USER, HABIT, TODAY, BigDecimal.ONE).value())
                .isEqualByComparingTo("1");
    }
}
