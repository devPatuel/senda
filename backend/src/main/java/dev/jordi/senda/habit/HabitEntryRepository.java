package dev.jordi.senda.habit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitEntryRepository extends JpaRepository<HabitEntry, Long> {
    Optional<HabitEntry> findByHabitIdAndEntryDate(Long habitId, LocalDate entryDate);
    List<HabitEntry> findByHabitIdAndEntryDateBetweenOrderByEntryDateAsc(Long habitId, LocalDate from, LocalDate to);
    List<HabitEntry> findByHabitIdOrderByEntryDateAsc(Long habitId);
    List<HabitEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);
    void deleteByHabitIdAndEntryDate(Long habitId, LocalDate entryDate);
}
