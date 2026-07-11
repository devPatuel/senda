package dev.jordi.senda.shoppinglist;

import java.math.BigDecimal;
import java.util.List;

public record ShoppingListResponse(List<ShoppingListItemResponse> items, BigDecimal estimatedTotal) {}
