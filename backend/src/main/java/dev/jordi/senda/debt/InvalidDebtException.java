package dev.jordi.senda.debt;

/**
 * Business-rule violation that maps to 400 Bad Request (e.g. payment exceeds
 * pending amount, or new originalAmount is less than what has already been paid).
 * Handled locally in {@link DebtController}.
 */
public class InvalidDebtException extends RuntimeException {

    public InvalidDebtException(String message) {
        super(message);
    }
}
