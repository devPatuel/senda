package dev.jordi.senda.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Where the personal discrepancy starts counting from. Movements are compared by
 * {@code created_at}, not by their date: a backdated movement typed after this
 * point was not in the balance it was measured against, so it must still count.
 */
@Entity
@Table(name = "personal_balance_baselines")
public class PersonalBaseline {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    protected PersonalBaseline() {
        // JPA only
    }

    public PersonalBaseline(Long userId, BigDecimal amount, Instant setAt) {
        this.userId = userId;
        this.amount = amount;
        this.setAt = setAt;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getSetAt() {
        return setAt;
    }

    public void setSetAt(Instant setAt) {
        this.setAt = setAt;
    }
}
