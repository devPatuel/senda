package dev.jordi.senda.habit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "habit_entries")
public class HabitEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "habit_id", nullable = false)
    private Long habitId;

    // Denormalised on purpose: lets every read filter by owner without joining habits.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal value;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    protected HabitEntry() { }

    public HabitEntry(Long habitId, Long userId, LocalDate entryDate, BigDecimal value, boolean done) {
        this.habitId = habitId;
        this.userId = userId;
        this.entryDate = entryDate;
        this.value = value;
        this.done = done;
    }

    public Long getId() { return id; }
    public Long getHabitId() { return habitId; }
    public Long getUserId() { return userId; }
    public LocalDate getEntryDate() { return entryDate; }
    public BigDecimal getValue() { return value; }
    public boolean isDone() { return done; }

    public void setValue(BigDecimal value) { this.value = value; }
    public void setDone(boolean done) { this.done = done; }
    public void touch() { this.updatedAt = Instant.now(); }
}
