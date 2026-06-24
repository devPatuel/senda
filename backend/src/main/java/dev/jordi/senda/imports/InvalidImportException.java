package dev.jordi.senda.imports;

/**
 * Business-rule violation while importing (e.g. a row's category type does not
 * match its income/expense sign). Maps to 400 via the controller handler.
 */
public class InvalidImportException extends RuntimeException {

    public InvalidImportException(String message) {
        super(message);
    }
}
