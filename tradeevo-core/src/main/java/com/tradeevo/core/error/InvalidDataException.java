package com.tradeevo.core.error;

/** A raw data file could not be read as JSON. */
public class InvalidDataException extends BadRequestException {

    public InvalidDataException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidDataException(String message) {
        super(message);
    }
}
