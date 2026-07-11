package dev.jordi.senda.product;

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

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // NULL = personal (F7). Non-null = couple space (F10). Kept for forward
    // compatibility; F7 always creates personal products.
    @Column(name = "space_id")
    private Long spaceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 10)
    private UnitType unitType;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Product() { }

    public Product(Long userId, String name, UnitType unitType, BigDecimal amount, String unit) {
        this.userId = userId;
        this.spaceId = null;
        this.name = name;
        this.unitType = unitType;
        this.amount = amount;
        this.unit = unit;
    }

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSpaceId() { return spaceId; }
    public String getName() { return name; }
    public UnitType getUnitType() { return unitType; }
    public BigDecimal getAmount() { return amount; }
    public String getUnit() { return unit; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setUnitType(UnitType unitType) { this.unitType = unitType; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setUnit(String unit) { this.unit = unit; }
}
