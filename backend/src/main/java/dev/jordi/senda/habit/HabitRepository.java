package dev.jordi.senda.habit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HabitRepository extends JpaRepository<Habit, Long> {
    List<Habit> findByUserIdAndActiveTrueOrderBySortOrderAscIdAsc(Long userId);
    List<Habit> findByUserIdOrderBySortOrderAscIdAsc(Long userId);
    Optional<Habit> findByIdAndUserId(Long id, Long userId);
}
