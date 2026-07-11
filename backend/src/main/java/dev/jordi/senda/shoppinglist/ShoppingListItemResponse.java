package dev.jordi.senda.shoppinglist;

import java.math.BigDecimal;

public record ShoppingListItemResponse(
        Long id, Long productId, String productName, Integer quantity, boolean checked,
        BigDecimal unitPrice, String supermarket, BigDecimal lineTotal) {}
