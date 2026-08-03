package dev.jordi.senda.habit;

import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.UnprocessableEntityException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class HabitService {

    private final HabitRepository repository;
    private final Clock clock;

    public HabitService(HabitRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<HabitResponse> list(Long userId, boolean includeArchived) {
        List<Habit> habits = includeArchived
                ? repository.findByUserIdOrderBySortOrderAscIdAsc(userId)
                : repository.findByUserIdAndActiveTrueOrderBySortOrderAscIdAsc(userId);
        return habits.stream().map(HabitResponse::from).toList();
    }

    @Transactional
    public HabitResponse create(Long userId, HabitRequest req) {
        validate(req);
        Habit habit = new Habit(userId, req.name().trim(), blankToNull(req.emoji()), req.type(),
                targetOf(req), blankToNull(req.unit()), req.scheduleType(), weekdaysOf(req),
                intervalOf(req), weeklyTargetOf(req),
                req.sortOrder() == null ? 0 : req.sortOrder());
        return HabitResponse.from(repository.save(habit));
    }

    @Transactional
    public HabitResponse update(Long userId, Long id, HabitRequest req) {
        validate(req);
        Habit habit = findOwned(userId, id);

        if (scheduleChanged(habit, req)) {
            // A streak never crosses this date: changing the rules restarts the count
            // instead of re-judging the past with rules that were not in force.
            habit.setScheduleChangedAt(LocalDate.now(clock));
        }

        habit.setName(req.name().trim());
        habit.setEmoji(blankToNull(req.emoji()));
        habit.setType(req.type());
        habit.setTarget(targetOf(req));
        habit.setUnit(blankToNull(req.unit()));
        habit.setScheduleType(req.scheduleType());
        habit.setWeekdays(weekdaysOf(req));
        habit.setIntervalDays(intervalOf(req));
        habit.setWeeklyTarget(weeklyTargetOf(req));
        if (req.sortOrder() != null) habit.setSortOrder(req.sortOrder());
        return HabitResponse.from(habit);
    }

    @Transactional
    public HabitResponse setArchived(Long userId, Long id, boolean archived) {
        Habit habit = findOwned(userId, id);
        habit.setActive(!archived);
        return HabitResponse.from(habit);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        // The entries go with it: habit_entries cascades on delete.
        repository.delete(findOwned(userId, id));
    }

    @Transactional(readOnly = true)
    public Habit findOwned(Long userId, Long id) {
        // 404 (not 403): never reveal another user's habit exists.
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Habit not found"));
    }

    private boolean scheduleChanged(Habit habit, HabitRequest req) {
        return habit.getScheduleType() != req.scheduleType()
                || !Objects.equals(habit.getWeekdays(), weekdaysOf(req))
                || !Objects.equals(habit.getIntervalDays(), intervalOf(req))
                || !Objects.equals(habit.getWeeklyTarget(), weeklyTargetOf(req));
    }

    private void validate(HabitRequest req) {
        if (req.type() == HabitType.COUNTER) {
            if (req.target() == null || req.target().signum() <= 0) {
                throw new UnprocessableEntityException("A counter habit needs a positive target");
            }
        } else if (req.target() != null) {
            throw new UnprocessableEntityException("Only counter habits have a target");
        }

        switch (req.scheduleType()) {
            case WEEKDAYS -> {
                String mask = req.weekdays();
                if (mask == null || !mask.matches("[01]{7}") || !mask.contains("1")) {
                    throw new UnprocessableEntityException(
                            "weekdays must be seven 0/1 characters with at least one day");
                }
            }
            case INTERVAL -> {
                if (req.intervalDays() == null || req.intervalDays() < 1 || req.intervalDays() > 365) {
                    throw new UnprocessableEntityException("intervalDays must be between 1 and 365");
                }
            }
            case WEEKLY_COUNT -> {
                if (req.weeklyTarget() == null || req.weeklyTarget() < 1 || req.weeklyTarget() > 7) {
                    throw new UnprocessableEntityException("weeklyTarget must be between 1 and 7");
                }
            }
        }
    }

    // Each schedule mode keeps only its own field; the others are stored as NULL so the
    // row cannot describe two schedules at once.
    private static String weekdaysOf(HabitRequest req) {
        return req.scheduleType() == ScheduleType.WEEKDAYS ? req.weekdays() : null;
    }

    private static Integer intervalOf(HabitRequest req) {
        return req.scheduleType() == ScheduleType.INTERVAL ? req.intervalDays() : null;
    }

    private static Integer weeklyTargetOf(HabitRequest req) {
        return req.scheduleType() == ScheduleType.WEEKLY_COUNT ? req.weeklyTarget() : null;
    }

    private static BigDecimal targetOf(HabitRequest req) {
        return req.type() == HabitType.COUNTER ? req.target() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
