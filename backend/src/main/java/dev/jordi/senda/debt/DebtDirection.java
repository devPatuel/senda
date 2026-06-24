package dev.jordi.senda.debt;

/**
 * Direction of a debt from the perspective of the logged-in user.
 */
public enum DebtDirection {
    /** Someone owes the user money. */
    THEY_OWE_ME,
    /** The user owes someone money. */
    I_OWE
}
