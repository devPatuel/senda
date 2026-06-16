package dev.jordi.senda.investment;

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

/**
 * A single buy ("lot") added to a holding. Lots are the audit trail behind the
 * holding's running quantity and weighted-average cost.
 */
@Entity
@Table(name = "holding_lots")
public class HoldingLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stored as the raw id (not a relation): a lot only ever belongs to one
    // holding and we never need to navigate back to the entity from here.
    @Column(name = "holding_id", nullable = false)
    private Long holdingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HoldingLot() {
        // JPA only
    }

    public HoldingLot(Long holdingId, Long userId, BigDecimal quantity, BigDecimal unitPrice, LocalDate date) {
        this.holdingId = holdingId;
        this.userId = userId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.date = date;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getHoldingId() {
        return holdingId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public LocalDate getDate() {
        return date;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
