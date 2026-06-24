package dev.jordi.senda.shopping;

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
@Table(name = "shopping_items")
public class ShoppingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_type", nullable = false, length = 10)
    private ShoppingListType listType;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "estimated_price", precision = 14, scale = 2)
    private BigDecimal estimatedPrice;

    @Column(name = "envelope_id")
    private Long envelopeId;

    @Column
    private Integer priority;

    @Column(nullable = false)
    private boolean bought = false;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ShoppingItem() { }

    public ShoppingItem(Long userId, ShoppingListType listType, String name, BigDecimal estimatedPrice,
                        Long envelopeId, Integer priority, String notes) {
        this.userId = userId;
        this.listType = listType;
        this.name = name;
        this.estimatedPrice = estimatedPrice;
        this.envelopeId = envelopeId;
        this.priority = priority;
        this.notes = notes;
        this.bought = false;
    }

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public ShoppingListType getListType() { return listType; }
    public String getName() { return name; }
    public BigDecimal getEstimatedPrice() { return estimatedPrice; }
    public Long getEnvelopeId() { return envelopeId; }
    public Integer getPriority() { return priority; }
    public boolean isBought() { return bought; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setEstimatedPrice(BigDecimal estimatedPrice) { this.estimatedPrice = estimatedPrice; }
    public void setEnvelopeId(Long envelopeId) { this.envelopeId = envelopeId; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public void setBought(boolean bought) { this.bought = bought; }
    public void setNotes(String notes) { this.notes = notes; }
}
