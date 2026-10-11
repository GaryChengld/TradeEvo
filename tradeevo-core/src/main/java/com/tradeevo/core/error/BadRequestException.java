package com.tradeevo.core.error;

/** The request, config or data is invalid. Maps to HTTP 400. */
public class BadRequestException extends TradeEvoException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
