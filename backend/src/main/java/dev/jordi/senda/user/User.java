package dev.jordi.senda.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    // Read-only on purpose: granted with SQL on the server, never through the app.
    @Column(nullable = false, updatable = false, insertable = false)
    private boolean admin;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {
        // JPA only
    }

    public User(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
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

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public boolean isAdmin() {
        return admin;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Keeps the row and its data; JwtAuthFilter stops accepting any of its credentials. */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    /**
     * Replaces the password and bumps the token version, which invalidates every
     * JWT issued before the change (see JwtAuthFilter).
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.tokenVersion++;
    }
}
