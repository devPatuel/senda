package dev.jordi.senda.networth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A point-in-time record of a user's net worth, captured once per day. Stored so
 * the patrimony chart can show the evolution over time.
 */
@Entity
@Table(name = "net_worth_snapshots")
public class NetWorthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false)
    private BigDecimal net;

    @Column(nullable = false)
    private BigDecimal liquid;

    @Column(nullable = false)
    private BigDecimal investments;

    @Column(name = "debts_in_favor", nullable = false)
    private BigDecimal debtsInFavor;

    @Column(name = "debts_against", nullable = false)
    private BigDecimal debtsAgainst;

    @Column(name = "couple_share", nullable = false)
    private BigDecimal coupleShare;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected NetWorthSnapshot() {
        // JPA only
    }

    public NetWorthSnapshot(Long userId, LocalDate snapshotDate, BigDecimal net, BigDecimal liquid,
                            BigDecimal investments, BigDecimal debtsInFavor, BigDecimal debtsAgainst,
                            BigDecimal coupleShare) {
        this.userId = userId;
        this.snapshotDate = snapshotDate;
        this.net = net;
        this.liquid = liquid;
        this.investments = investments;
        this.debtsInFavor = debtsInFavor;
        this.debtsAgainst = debtsAgainst;
        this.coupleShare = coupleShare;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public BigDecimal getNet() {
        return net;
    }

    public BigDecimal getLiquid() {
        return liquid;
    }

    public BigDecimal getInvestments() {
        return investments;
    }

    public BigDecimal getDebtsInFavor() {
        return debtsInFavor;
    }

    public BigDecimal getDebtsAgainst() {
        return debtsAgainst;
    }

    public BigDecimal getCoupleShare() {
        return coupleShare;
    }
}
