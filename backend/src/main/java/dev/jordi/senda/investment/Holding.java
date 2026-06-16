package dev.jordi.senda.investment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "holdings")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_class_id", nullable = false)
    private AssetClass assetClass;

    @Column(nullable = false, length = 40)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "avg_cost", nullable = false, precision = 20, scale = 8)
    private BigDecimal avgCost;

    @Column(name = "current_price", precision = 20, scale = 8)
    private BigDecimal currentPrice;

    @Column(name = "last_priced_at")
    private Instant lastPricedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Holding() {
        // JPA only
    }

    public Holding(Long userId, AssetClass assetClass, String symbol, String name,
                   BigDecimal quantity, BigDecimal avgCost) {
        this.userId = userId;
        this.assetClass = assetClass;
        this.symbol = symbol;
        this.name = name;
        this.quantity = quantity;
        this.avgCost = avgCost;
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

    public Long getUserId() {
        return userId;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public void setAvgCost(BigDecimal avgCost) {
        this.avgCost = avgCost;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public Instant getLastPricedAt() {
        return lastPricedAt;
    }

    public void setLastPricedAt(Instant lastPricedAt) {
        this.lastPricedAt = lastPricedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
