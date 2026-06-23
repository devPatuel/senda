package dev.jordi.senda.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Persisted balance of an expense category, used as a budgeting "envelope".
 * One row per expense category (1-to-1). The balance only changes through
 * explicit budgeting actions (assign / distribute), never automatically from
 * transactions, and may be negative.
 */
@Entity
@Table(name = "category_balances")
public class CategoryBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false, unique = true)
    private Long categoryId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal balance;

    protected CategoryBalance() {
        // JPA only
    }

    public CategoryBalance(Long categoryId, Long userId) {
        this.categoryId = categoryId;
        this.userId = userId;
        this.balance = BigDecimal.ZERO.setScale(2);
    }

    public Long getId() {
        return id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
