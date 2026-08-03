package dev.jordi.senda.habit;

import dev.jordi.senda.TestcontainersConfiguration;
import dev.jordi.senda.user.User;
import dev.jordi.senda.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HabitRepositoryTest {

    @Autowired private HabitRepository habits;
    @Autowired private HabitEntryRepository entries;
    @Autowired private UserRepository users;

    private Long newUser(String email) {
        return users.save(new User(email, "x", "U")).getId();
    }

    private Habit newHabit(Long userId, String name) {
        return habits.save(new Habit(userId, name, "📖", HabitType.CHECK, null, null,
                ScheduleType.WEEKDAYS, "1111111", null, null, 0));
    }

    @Test
    void findByIdAndUserId_scopesByOwner() {
        Long userA = newUser("hab-a@test.dev");
        Long userB = newUser("hab-b@test.dev");
        Habit ofA = newHabit(userA, "Leer");

        assertThat(habits.findByIdAndUserId(ofA.getId(), userA)).isPresent();
        assertThat(habits.findByIdAndUserId(ofA.getId(), userB)).isEmpty();
    }

    @Test
    void archivedHabitsAreExcludedFromTheActiveList() {
        Long userId = newUser("hab-archived@test.dev");
        newHabit(userId, "Activo");
        Habit archived = newHabit(userId, "Archivado");
        archived.setActive(false);
        habits.save(archived);

        assertThat(habits.findByUserIdAndActiveTrueOrderBySortOrderAscIdAsc(userId))
                .extracting(Habit::getName).containsExactly("Activo");
        assertThat(habits.findByUserIdOrderBySortOrderAscIdAsc(userId)).hasSize(2);
    }

    @Test
    void oneEntryPerHabitAndDay() {
        Long userId = newUser("hab-entry@test.dev");
        Habit habit = newHabit(userId, "Agua");
        LocalDate day = LocalDate.of(2026, 8, 3);
        entries.save(new HabitEntry(habit.getId(), userId, day, new BigDecimal("5.00"), false));

        assertThat(entries.findByHabitIdAndEntryDate(habit.getId(), day)).isPresent();
        assertThat(entries.findByHabitIdAndEntryDate(habit.getId(), day.minusDays(1))).isEmpty();
    }
}
