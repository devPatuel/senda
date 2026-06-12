package dev.jordi.senda.common;

/**
 * Shared type for both categories and transactions. A single enum keeps the
 * service-layer rule "transaction type must match category type" trivial to
 * check without mapping between two parallel enums.
 */
public enum TransactionType {
    INCOME,
    EXPENSE
}
