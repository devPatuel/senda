package dev.jordi.senda.account;

/**
 * Kind of liquid-money account. Module-local (unlike the shared
 * {@code TransactionType}) because nothing outside the account module needs it.
 */
public enum AccountType {
    BANK,
    CASH
}
