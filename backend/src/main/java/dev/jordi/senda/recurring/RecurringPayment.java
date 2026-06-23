package dev.jordi.senda.recurring;

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

/**
 * A payment that repeats on a fixed schedule (subscriptions, insurance...).
 * Forecast-only: it never creates transactions. The next due date is derived
 * from {@code dayOfMonth}/{@code month}, not stored, so it is always current.
 */
@Entity
@Table(name = "recurring_payments")
public class RecurringPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecurringFrequency frequency;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    // Required for ANNUAL and QUARTERLY (the anchor month); null otherwise.
    @Column
    private Integer month;

    // ISO day of week (1=Monday..7=Sunday); only used for WEEKLY, null otherwise.
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    // Optional cancellation deadline; informational reminder only.
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected RecurringPayment() {
        // JPA only
    }

    public RecurringPayment(Long userId, String name, BigDecimal amount, RecurringFrequency frequency,
                            Long categoryId, int dayOfMonth, Integer month, Integer dayOfWeek, LocalDate endDate) {
        this.userId = userId;
        this.name = name;
        this.amount = amount;
        this.frequency = frequency;
        this.categoryId = categoryId;
        this.dayOfMonth = dayOfMonth;
        this.month = month;
        this.dayOfWeek = dayOfWeek;
        this.endDate = endDate;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public RecurringFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(RecurringFrequency frequency) {
        this.frequency = frequency;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(int dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
