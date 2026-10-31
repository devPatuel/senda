package dev.jordi.senda.wishlist;

import java.math.BigDecimal;
import java.util.List;

/** {@code total} covers every wish; {@code unlistedTotal} only those in no list. */
public record WishlistResponse(List<WishlistItemResponse> items, BigDecimal total,
                               List<WishListSummary> lists, BigDecimal unlistedTotal) {}
