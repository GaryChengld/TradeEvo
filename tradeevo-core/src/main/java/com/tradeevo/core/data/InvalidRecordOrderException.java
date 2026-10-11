package com.tradeevo.core.data;

/** Record timestamps are missing, unparseable, duplicated or out of order. Fails the run. */
public class InvalidRecordOrderException extends RuntimeException {

    public InvalidRecordOrderException(String message) {
        super(message);
    }
}
