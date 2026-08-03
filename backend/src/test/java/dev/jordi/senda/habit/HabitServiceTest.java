package dev.jordi.senda.habit;

import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.UnprocessableEntityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HabitServiceTest {

    private static final Long USER = 1L;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneId.of("Europe/Madrid"));

    private HabitRepository repository;
    private HabitService service;

    @BeforeEach
    void setUp() {
        repository = mock(HabitRepository.class);
        service = new HabitService(repository, CLOCK);
        when(repository.save(any(Habit.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void counterWithoutTargetIsRejected() {
        HabitRequest noTarget = new HabitRequest("Agua", null, HabitType.COUNTER, null, "vasos",
                ScheduleType.WEEKDAYS, "1111111", null, null, 0);

        assertThatThrownBy(() -> service.create(USER, noTarget))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("target");
    }

    @Test
    void checkHabitWithATargetIsRejected() {
        HabitRequest withTarget = new HabitRequest("Meditar", null, HabitType.CHECK,
                new BigDecimal("5"), null, ScheduleType.WEEKDAYS, "1111111", null, null, 0);

        assertThatThrownBy(() -> service.create(USER, withTarget))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void weekdaysScheduleRequiresAValidMask() {
        HabitRequest badMask = new HabitRequest("Leer", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, "111", null, null, 0);

        assertThatThrownBy(() -> service.create(USER, badMask))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("weekdays");
    }

    @Test
    void anAllZeroMaskIsRejected() {
        HabitRequest noDays = new HabitRequest("Leer", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, "0000000", null, null, 0);

        assertThatThrownBy(() -> service.create(USER, noDays))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void intervalScheduleRequiresIntervalDays() {
        HabitRequest noInterval = new HabitRequest("Afeitarme", null, HabitType.CHECK, null, null,
                ScheduleType.INTERVAL, null, null, null, 0);

        assertThatThrownBy(() -> service.create(USER, noInterval))
                .isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void createClearsTheFieldsOfTheOtherScheduleModes() {
        HabitRequest mixed = new HabitRequest("Gym", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKLY_COUNT, "1111111", 3, 3, 0);

        HabitResponse created = service.create(USER, mixed);

        assertThat(created.weeklyTarget()).isEqualTo(3);
        assertThat(created.weekdays()).isNull();
        assertThat(created.intervalDays()).isNull();
    }

    @Test
    void changingTheScheduleSealsTheChangeDate() {
        Habit existing = new Habit(USER, "Gym", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, "1010100", null, null, 0);
        when(repository.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(existing));

        service.update(USER, 5L, new HabitRequest("Gym", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, "1111111", null, null, 0));

        assertThat(existing.getScheduleChangedAt()).isEqualTo("2026-08-03");
    }

    @Test
    void renamingDoesNotResetTheStreak() {
        Habit existing = new Habit(USER, "Gym", null, HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, "1010100", null, null, 0);
        when(repository.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(existing));

        service.update(USER, 5L, new HabitRequest("Gimnasio", "💪", HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, "1010100", null, null, 0));

        assertThat(existing.getScheduleChangedAt()).isNull();
    }

    @Test
    void anotherUsersHabitIsNotFound() {
        when(repository.findByIdAndUserId(9L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setArchived(USER, 9L, true))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listExcludesArchivedUnlessAsked() {
        // Both stubs are required: an unstubbed Mockito call returns null and .stream()
        // would blow up inside the service.
        when(repository.findByUserIdAndActiveTrueOrderBySortOrderAscIdAsc(USER)).thenReturn(List.of());
        when(repository.findByUserIdOrderBySortOrderAscIdAsc(USER)).thenReturn(List.of());

        service.list(USER, false);
        service.list(USER, true);

        verify(repository).findByUserIdAndActiveTrueOrderBySortOrderAscIdAsc(USER);
        verify(repository).findByUserIdOrderBySortOrderAscIdAsc(USER);
    }
}
