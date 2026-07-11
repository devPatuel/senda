package dev.jordi.senda.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_entries")
public class PriceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Scope is inherited from the product; access always goes through it.
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 80)
    private String supermarket;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected PriceEntry() { }

    public PriceEntry(Long productId, BigDecimal price, String supermarket) {
        this.productId = productId;
        this.price = price;
        this.supermarket = supermarket;
    }

    @PrePersist
    void onCreate() { if (recordedAt == null) recordedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public BigDecimal getPrice() { return price; }
    public String getSupermarket() { return supermarket; }
    public Instant getRecordedAt() { return recordedAt; }
}
