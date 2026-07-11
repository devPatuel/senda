package dev.jordi.senda.wishlist;

import java.math.BigDecimal;
import java.time.Instant;

public record WishlistItemResponse(
        Long id, String name, String imageUrl, String productUrl,
        String comment, BigDecimal price, Integer priority, Instant createdAt) {

    static WishlistItemResponse from(WishlistItem i) {
        return new WishlistItemResponse(i.getId(), i.getName(), i.getImageUrl(),
                i.getProductUrl(), i.getComment(), i.getPrice(), i.getPriority(), i.getCreatedAt());
    }
}
