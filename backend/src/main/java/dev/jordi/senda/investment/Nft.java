package dev.jordi.senda.investment;

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
@Table(name = "nfts")
public class Nft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String collection;

    @Column(name = "buy_crypto_symbol", nullable = false, length = 40)
    private String buyCryptoSymbol;

    @Column(name = "buy_crypto_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal buyCryptoAmount;

    @Column(name = "fiat_value_at_purchase", nullable = false, precision = 14, scale = 2)
    private BigDecimal fiatValueAtPurchase;

    @Column(name = "our_current_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal ourCurrentValue;

    @Column(length = 500)
    private String utility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Nft() {
        // JPA only
    }

    public Nft(Long userId, String name, String collection, String buyCryptoSymbol,
               BigDecimal buyCryptoAmount, BigDecimal fiatValueAtPurchase,
               BigDecimal ourCurrentValue, String utility) {
        this.userId = userId;
        this.name = name;
        this.collection = collection;
        this.buyCryptoSymbol = buyCryptoSymbol;
        this.buyCryptoAmount = buyCryptoAmount;
        this.fiatValueAtPurchase = fiatValueAtPurchase;
        this.ourCurrentValue = ourCurrentValue;
        this.utility = utility;
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

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getBuyCryptoSymbol() {
        return buyCryptoSymbol;
    }

    public void setBuyCryptoSymbol(String buyCryptoSymbol) {
        this.buyCryptoSymbol = buyCryptoSymbol;
    }

    public BigDecimal getBuyCryptoAmount() {
        return buyCryptoAmount;
    }

    public void setBuyCryptoAmount(BigDecimal buyCryptoAmount) {
        this.buyCryptoAmount = buyCryptoAmount;
    }

    public BigDecimal getFiatValueAtPurchase() {
        return fiatValueAtPurchase;
    }

    public void setFiatValueAtPurchase(BigDecimal fiatValueAtPurchase) {
        this.fiatValueAtPurchase = fiatValueAtPurchase;
    }

    public BigDecimal getOurCurrentValue() {
        return ourCurrentValue;
    }

    public void setOurCurrentValue(BigDecimal ourCurrentValue) {
        this.ourCurrentValue = ourCurrentValue;
    }

    public String getUtility() {
        return utility;
    }

    public void setUtility(String utility) {
        this.utility = utility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
