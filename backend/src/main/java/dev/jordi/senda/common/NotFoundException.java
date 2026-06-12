package dev.jordi.senda.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
