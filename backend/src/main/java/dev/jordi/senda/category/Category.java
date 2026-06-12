package dev.jordi.senda.category;

import dev.jordi.senda.common.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(nullable = false)
    private boolean active = true;

    protected Category() {
        // JPA only
    }

    public Category(Long userId, String name, TransactionType type, String color) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.color = color;
        this.active = true;
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

    public TransactionType getType() {
        return type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
