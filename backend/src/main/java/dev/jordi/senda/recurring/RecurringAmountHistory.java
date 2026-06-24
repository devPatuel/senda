package dev.jordi.senda.recurring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One recorded amount for a recurring payment: the baseline at creation and a new
 * row on every amount change. Used to surface the last price variation.
 */
@Entity
@Table(name = "recurring_amount_history")
public class RecurringAmountHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recurring_id", nullable = false)
    private Long recurringId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @PrePersist
    void onCreate() {
        this.changedAt = Instant.now();
    }

    protected RecurringAmountHistory() {
        // JPA only
    }

    public RecurringAmountHistory(Long recurringId, BigDecimal amount) {
        this.recurringId = recurringId;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Long getRecurringId() {
        return recurringId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
