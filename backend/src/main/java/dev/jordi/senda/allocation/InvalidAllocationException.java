package dev.jordi.senda.allocation;

/**
 * Business-rule violation specific to the allocation module that maps to
 * 400 Bad Request. Handled locally in {@link AllocationController}.
 */
public class InvalidAllocationException extends RuntimeException {

    public InvalidAllocationException(String message) {
        super(message);
    }
}
