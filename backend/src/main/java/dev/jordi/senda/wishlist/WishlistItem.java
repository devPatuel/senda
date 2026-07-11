package dev.jordi.senda.wishlist;

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
@Table(name = "wishlist_items")
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Reserved for the future shared-couple variant; always NULL in the personal scope.
    @Column(name = "space_id")
    private Long spaceId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "product_url", length = 1000)
    private String productUrl;

    @Column(length = 1000)
    private String comment;

    @Column(precision = 14, scale = 2)
    private BigDecimal price;

    @Column
    private Integer priority;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WishlistItem() { }

    public WishlistItem(Long userId, String name, String imageUrl, String productUrl,
                        String comment, BigDecimal price, Integer priority) {
        this.userId = userId;
        this.name = name;
        this.imageUrl = imageUrl;
        this.productUrl = productUrl;
        this.comment = comment;
        this.price = price;
        this.priority = priority;
    }

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSpaceId() { return spaceId; }
    public String getName() { return name; }
    public String getImageUrl() { return imageUrl; }
    public String getProductUrl() { return productUrl; }
    public String getComment() { return comment; }
    public BigDecimal getPrice() { return price; }
    public Integer getPriority() { return priority; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setProductUrl(String productUrl) { this.productUrl = productUrl; }
    public void setComment(String comment) { this.comment = comment; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setPriority(Integer priority) { this.priority = priority; }
}
