package dev.jordi.senda.recurring;

/** Business-rule violation in the recurring module; maps to 400 Bad Request. */
public class InvalidRecurringException extends RuntimeException {
    public InvalidRecurringException(String message) {
        super(message);
    }
}
