package com.tradeevo.core.data;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Raw data handed to a strategy, unparsed.
 *
 * @param date    the file's date, or null for data supplied in an evaluation request
 * @param content the JSON content, in the strategy's own format
 */
public record DataFile(LocalDate date, JsonNode content) {

    /** A short label for messages: the date, or "request data". */
    public String label() {
        return date != null ? date.toString() : "request data";
    }
}
