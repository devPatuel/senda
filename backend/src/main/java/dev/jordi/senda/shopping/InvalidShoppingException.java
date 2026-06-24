package dev.jordi.senda.shopping;

/**
 * Business-rule violation that maps to 400 Bad Request.
 * Handled locally in {@link ShoppingController}.
 */
public class InvalidShoppingException extends RuntimeException {
    public InvalidShoppingException(String message) { super(message); }
}
