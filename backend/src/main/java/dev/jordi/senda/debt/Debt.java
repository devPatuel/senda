package dev.jordi.senda.debt;

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

@Entity
@Table(name = "debts")
public class Debt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DebtDirection direction;

    @Column(nullable = false, length = 100)
    private String counterparty;

    @Column(nullable = false, length = 255)
    private String concept;

    @Column(name = "original_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal originalAmount;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private boolean settled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Debt() {
        // JPA only
    }

    public Debt(Long userId, DebtDirection direction, String counterparty, String concept,
                BigDecimal originalAmount, LocalDate date) {
        this.userId = userId;
        this.direction = direction;
        this.counterparty = counterparty;
        this.concept = concept;
        this.originalAmount = originalAmount;
        this.date = date;
        this.settled = false;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public DebtDirection getDirection() { return direction; }
    public void setDirection(DebtDirection direction) { this.direction = direction; }
    public String getCounterparty() { return counterparty; }
    public void setCounterparty(String counterparty) { this.counterparty = counterparty; }
    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public boolean isSettled() { return settled; }
    public void setSettled(boolean settled) { this.settled = settled; }
    public Instant getCreatedAt() { return createdAt; }
}
