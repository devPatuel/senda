package dev.jordi.senda.common;

/**
 * The request is well formed but its content breaks a domain rule (422).
 * Kept apart from the 400 of field validation so the UI can tell "the name is
 * missing" from "that day is outside the editable window".
 */
public class UnprocessableEntityException extends RuntimeException {
    public UnprocessableEntityException(String message) {
        super(message);
    }
}
