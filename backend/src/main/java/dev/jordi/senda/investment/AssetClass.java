package dev.jordi.senda.investment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "asset_classes")
public class AssetClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_source", nullable = false, length = 10)
    private PricingSource pricingSource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssetClass() {
        // JPA only
    }

    public AssetClass(Long userId, String name, PricingSource pricingSource) {
        this.userId = userId;
        this.name = name;
        this.pricingSource = pricingSource;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PricingSource getPricingSource() {
        return pricingSource;
    }

    public void setPricingSource(PricingSource pricingSource) {
        this.pricingSource = pricingSource;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
