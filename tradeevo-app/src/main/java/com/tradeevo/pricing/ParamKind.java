package com.tradeevo.pricing;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/** The value rules for pricing parameters: one source for both the JSON checks and the records. */
enum ParamKind {
    FINITE, POSITIVE, POSITIVE_INTEGER, TIME_OF_DAY;

    private static final Pattern HH_MM = Pattern.compile("\\d{2}:\\d{2}");

    /** Why a JSON value is invalid for this kind, or null if it is valid. */
    String problem(JsonNode value) {
        return switch (this) {
            case TIME_OF_DAY -> {
                if (!value.isTextual() || !HH_MM.matcher(value.asText()).matches()) {
                    yield "must be a time HH:mm";
                }
                try {
                    LocalTime.parse(value.asText());
                    yield null;
                } catch (DateTimeParseException e) {
                    yield "must be a valid time HH:mm";
                }
            }
            case POSITIVE_INTEGER -> value.isIntegralNumber() && value.canConvertToInt() && value.intValue() > 0
                    ? null
                    : "must be a positive integer";
            default -> value.isNumber() ? problem(value.doubleValue()) : "must be a number";
        };
    }

    /** Why a number is invalid for {@link #FINITE} or {@link #POSITIVE}, or null if it is valid. */
    String problem(double value) {
        if (!Double.isFinite(value)) {
            return "must be a finite number";
        }
        if ((this == POSITIVE || this == POSITIVE_INTEGER) && value <= 0) {
            return "must be > 0";
        }
        return null;
    }

    /** Returns the value, or throws {@link IllegalArgumentException} naming the parameter. */
    double require(String name, double value) {
        String problem = problem(value);
        if (problem != null) {
            throw new IllegalArgumentException(name + ": " + problem + ", was " + value);
        }
        return value;
    }
}
