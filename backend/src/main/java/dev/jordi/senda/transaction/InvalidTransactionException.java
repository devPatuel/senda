package dev.jordi.senda.transaction;

/**
 * Business-rule violation that maps to 400 Bad Request (e.g. transaction type
 * not matching the category type, or invalid paging/summary parameters).
 * Handled locally in {@link TransactionController}.
 */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
