package dev.jordi.senda.wishlist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WishListRequest(@NotBlank @Size(max = 80) String name) {}
