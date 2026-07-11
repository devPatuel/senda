package dev.jordi.senda.shoppinglist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "shopping_list_items")
public class ShoppingListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Reserved for the future shared-couple variant; always NULL in the personal scope.
    @Column(name = "space_id")
    private Long spaceId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private boolean checked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ShoppingListItem() { }

    public ShoppingListItem(Long userId, Long productId, Integer quantity) {
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity == null ? 1 : quantity;
        this.checked = false;
    }

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSpaceId() { return spaceId; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public boolean isChecked() { return checked; }
    public Instant getCreatedAt() { return createdAt; }

    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setChecked(boolean checked) { this.checked = checked; }
}
