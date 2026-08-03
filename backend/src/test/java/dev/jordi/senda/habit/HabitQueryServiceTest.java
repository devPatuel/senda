package dev.jordi.senda.habit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HabitQueryServiceTest {

    private static final Long USER = 1L;
    // Monday
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 3);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneId.of("Europe/Madrid"));

    private HabitRepository habits;
    private HabitEntryRepository entries;
    private HabitQueryService service;

    @BeforeEach
    void setUp() {
        habits = mock(HabitRepository.class);
        entries = mock(HabitEntryRepository.class);
        service = new HabitQueryService(habits, entries, CLOCK);
        when(entries.findByHabitIdOrderByEntryDateAsc(anyLong())).thenReturn(List.of());
        when(entries.findByUserIdAndEntryDate(any(), any())).thenReturn(List.of());
    }

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    private Habit weekdayHabit(Long id, String name, String mask) {
        Habit habit = new Habit(USER, name, null, HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, mask, null, null, 0);
        habit.setId(id);
        habit.setCreatedAt(CREATED);
        return habit;
    }

    @Test
    void todayOnlyListsWhatIsScheduledForToday() {
        Habit daily = weekdayHabit(1L, "Leer", "1111111");
        Habit weekendOnly = weekdayHabit(2L, "Peli", "0000011");
        when(habits.findByUserIdAndActiveTrueOrderBySortOrderAscIdAsc(USER))
                .thenReturn(List.of(daily, weekendOnly));

        List<TodayHabitResponse> today = service.today(USER);

        assertThat(today).extracting(TodayHabitResponse::name).containsExactly("Leer");
    }

    @Test
    void todayCarriesTheValueAlreadyRecorded() {
        Habit water = new Habit(USER, "Agua", "💧", HabitType.COUNTER, new BigDecimal("8"), "vasos",
                ScheduleType.WEEKDAYS, "1111111", null, null, 0);
        water.setId(3L);
        water.setCreatedAt(CREATED);
        when(habits.findByUserIdAndActiveTrueOrderBySortOrderAscIdAsc(USER)).thenReturn(List.of(water));
        when(entries.findByUserIdAndEntryDate(USER, TODAY))
                .thenReturn(List.of(new HabitEntry(3L, USER, TODAY, new BigDecimal("5"), false)));

        TodayHabitResponse response = service.today(USER).get(0);

        assertThat(response.value()).isEqualByComparingTo("5");
        assertThat(response.done()).isFalse();
        assertThat(response.target()).isEqualByComparingTo("8");
    }

    @Test
    void historyReturnsEntriesStreaksAndCompletionRate() {
        Habit daily = weekdayHabit(1L, "Leer", "1111111");
        when(habits.findByIdAndUserId(1L, USER)).thenReturn(java.util.Optional.of(daily));
        when(entries.findByHabitIdAndEntryDateBetweenOrderByEntryDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        new HabitEntry(1L, USER, TODAY.minusDays(2), null, true),
                        new HabitEntry(1L, USER, TODAY.minusDays(1), null, true)));
        when(entries.findByHabitIdOrderByEntryDateAsc(1L))
                .thenReturn(List.of(
                        new HabitEntry(1L, USER, TODAY.minusDays(2), null, true),
                        new HabitEntry(1L, USER, TODAY.minusDays(1), null, true)));

        HabitHistoryResponse history =
                service.history(USER, 1L, TODAY.minusDays(2), TODAY);

        assertThat(history.entries()).hasSize(2);
        assertThat(history.currentStreak()).isEqualTo(2);
        // Two scheduled days completed out of the two already closed (today is still open).
        assertThat(history.completionRate()).isEqualTo(100);
    }
}
