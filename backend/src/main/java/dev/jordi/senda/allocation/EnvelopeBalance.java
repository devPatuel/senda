package dev.jordi.senda.allocation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "envelope_balances")
public class EnvelopeBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "envelope_id", nullable = false, unique = true)
    private Long envelopeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    protected EnvelopeBalance() {
        // JPA only
    }

    public EnvelopeBalance(Long envelopeId, Long userId) {
        this.envelopeId = envelopeId;
        this.userId = userId;
        this.balance = BigDecimal.ZERO.setScale(2);
    }

    public Long getId() { return id; }
    public Long getEnvelopeId() { return envelopeId; }
    public Long getUserId() { return userId; }
    public BigDecimal getBalance() { return balance; }

    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
