package com.tradeevo.core.error;

/** Base class for expected, user-facing errors. */
public class TradeEvoException extends RuntimeException {

    public TradeEvoException(String message) {
        super(message);
    }

    public TradeEvoException(String message, Throwable cause) {
        super(message, cause);
    }
}
