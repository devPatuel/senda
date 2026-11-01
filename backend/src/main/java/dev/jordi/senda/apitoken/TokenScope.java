package dev.jordi.senda.apitoken;

/** What a personal access token may do; the rules live in SecurityConfig. */
public enum TokenScope {
    /** Quick capture from a shortcut: pick a category and create the movement. */
    QUICK,
    /** The assistant: read summary, budget, categories, accounts and spaces, and create movements. */
    ASSISTANT
}
