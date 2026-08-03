package dev.jordi.senda.habit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Entity
@Table(name = "habits")
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Reserved for the future shared-couple variant; always NULL in the personal scope.
    @Column(name = "space_id")
    private Long spaceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 8)
    private String emoji;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HabitType type;

    @Column(precision = 10, scale = 2)
    private BigDecimal target;

    @Column(length = 20)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 12)
    private ScheduleType scheduleType;

    // Seven characters, Monday first: "1010100" is Mon/Wed/Fri.
    @Column(length = 7)
    private String weekdays;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "weekly_target")
    private Integer weeklyTarget;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // Sealed when the schedule changes: a streak never crosses this date.
    @Column(name = "schedule_changed_at")
    private LocalDate scheduleChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    protected Habit() { }

    public Habit(Long userId, String name, String emoji, HabitType type, BigDecimal target,
                 String unit, ScheduleType scheduleType, String weekdays, Integer intervalDays,
                 Integer weeklyTarget, int sortOrder) {
        this.userId = userId;
        this.name = name;
        this.emoji = emoji;
        this.type = type;
        this.target = target;
        this.unit = unit;
        this.scheduleType = scheduleType;
        this.weekdays = weekdays;
        this.intervalDays = intervalDays;
        this.weeklyTarget = weeklyTarget;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmoji() { return emoji; }
    public HabitType getType() { return type; }
    public BigDecimal getTarget() { return target; }
    public String getUnit() { return unit; }
    public ScheduleType getScheduleType() { return scheduleType; }
    public String getWeekdays() { return weekdays; }
    public Integer getIntervalDays() { return intervalDays; }
    public Integer getWeeklyTarget() { return weeklyTarget; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public LocalDate getScheduleChangedAt() { return scheduleChangedAt; }
    public Instant getCreatedAt() { return createdAt; }

    /** The day the habit started existing, in the app's zone. Nothing before it is judged. */
    public LocalDate getCreatedOn(ZoneId zone) {
        return LocalDate.ofInstant(createdAt, zone);
    }

    public void setName(String name) { this.name = name; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public void setType(HabitType type) { this.type = type; }
    public void setTarget(BigDecimal target) { this.target = target; }
    public void setUnit(String unit) { this.unit = unit; }
    public void setScheduleType(ScheduleType scheduleType) { this.scheduleType = scheduleType; }
    public void setWeekdays(String weekdays) { this.weekdays = weekdays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }
    public void setWeeklyTarget(Integer weeklyTarget) { this.weeklyTarget = weeklyTarget; }
    public void setActive(boolean active) { this.active = active; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setScheduleChangedAt(LocalDate date) { this.scheduleChangedAt = date; }

    // Package-private hooks for tests only: they let a test build a habit with a fixed
    // id and creation date without going through the database.
    void setId(Long id) { this.id = id; }
    void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
