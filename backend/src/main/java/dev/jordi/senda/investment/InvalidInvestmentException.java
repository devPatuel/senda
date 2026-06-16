package dev.jordi.senda.investment;

/**
 * Business-rule violation that maps to 400 Bad Request (e.g. a buy on an empty
 * resulting position). Handled locally in the investment controller(s), mirroring
 * {@code InvalidTransactionException}.
 */
public class InvalidInvestmentException extends RuntimeException {

    public InvalidInvestmentException(String message) {
        super(message);
    }
}
