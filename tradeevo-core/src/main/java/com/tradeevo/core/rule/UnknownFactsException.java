package com.tradeevo.core.rule;

/** Thrown when a rule with {@link UnknownPolicy#FAIL} cannot be decided. Fails the run. */
public class UnknownFactsException extends RuntimeException {

    public UnknownFactsException(String message) {
        super(message);
    }
}
