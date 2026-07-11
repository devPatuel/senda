package dev.jordi.senda.wishlist;

import java.math.BigDecimal;
import java.util.List;

public record WishlistResponse(List<WishlistItemResponse> items, BigDecimal total) {}
