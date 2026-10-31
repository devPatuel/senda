package dev.jordi.senda.wishlist;

import java.math.BigDecimal;

public record WishListSummary(Long id, String name, long itemCount, BigDecimal total) {}
