package com.tradeevo.core.error;

/** A strategy, run or data set does not exist. Maps to HTTP 404. */
public class NotFoundException extends TradeEvoException {

    public NotFoundException(String message) {
        super(message);
    }
}
