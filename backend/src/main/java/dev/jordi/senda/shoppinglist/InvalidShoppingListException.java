package dev.jordi.senda.shoppinglist;

public class InvalidShoppingListException extends RuntimeException {
    public InvalidShoppingListException(String message) {
        super(message);
    }
}
