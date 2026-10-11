package com.tradeevo.core.error;

/** The write would overwrite something that must stay immutable. Maps to HTTP 409. */
public class ConflictException extends TradeEvoException {

    public ConflictException(String message) {
        super(message);
    }
}
