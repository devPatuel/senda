package dev.jordi.senda.investment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * NFT view. {@code currentPurchaseValue} is the computed "what does the crypto
 * you paid cost today" = buyCryptoAmount * current price of buyCryptoSymbol. It
 * is {@code null} when no live crypto price is available.
 */
public record NftResponse(
        Long id,
        String name,
        String collection,
        String buyCryptoSymbol,
        BigDecimal buyCryptoAmount,
        BigDecimal fiatValueAtPurchase,
        BigDecimal ourCurrentValue,
        String utility,
        BigDecimal currentPurchaseValue,
        Instant createdAt) {
}
