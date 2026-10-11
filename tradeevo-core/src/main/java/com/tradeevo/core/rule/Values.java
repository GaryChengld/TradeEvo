package com.tradeevo.core.rule;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/** Type-tolerant value helpers shared by the core rules. */
public final class Values {

    private Values() {
    }

    /** Converts a JSON value to a plain Java value (Number, String, Boolean, List, Map or null). */
    public static Object fromJson(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>(node.size());
            node.forEach(element -> list.add(fromJson(element)));
            return list;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        node.properties().forEach(entry -> map.put(entry.getKey(), fromJson(entry.getValue())));
        return map;
    }

    /**
     * Compares two values of compatible types: numbers by exact decimal value, otherwise values of the
     * same {@link Comparable} type. Returns empty when the values cannot be compared, including when
     * either number is NaN or infinite.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional<Integer> compare(Object left, Object right) {
        if (left == null || right == null) {
            return Optional.empty();
        }
        if (left instanceof Number l && right instanceof Number r) {
            BigDecimal a = toDecimal(l);
            BigDecimal b = toDecimal(r);
            return a == null || b == null ? Optional.empty() : Optional.of(a.compareTo(b));
        }
        if (left.getClass() == right.getClass() && left instanceof Comparable comparable) {
            return Optional.of(Integer.signum(comparable.compareTo(right)));
        }
        return Optional.empty();
    }

    /**
     * The exact decimal value of a number, or null if it is NaN, infinite or not numeric. Integral
     * types and {@link BigDecimal}/{@link BigInteger} keep full precision; doubles and floats use their
     * shortest decimal representation (so {@code 0.1d} equals {@code new BigDecimal("0.1")}).
     */
    public static BigDecimal toDecimal(Number number) {
        if (number instanceof BigDecimal decimal) {
            return decimal;
        }
        if (number instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (number instanceof Long || number instanceof Integer || number instanceof Short || number instanceof Byte) {
            return BigDecimal.valueOf(number.longValue());
        }
        if (number instanceof Float f) {
            return Float.isFinite(f) ? new BigDecimal(Float.toString(f)) : null;
        }
        if (number instanceof Double d) {
            return Double.isFinite(d) ? BigDecimal.valueOf(d) : null;
        }
        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Equality that treats numbers of different Java types (e.g. 1 and 1.0) as equal. */
    public static boolean looseEquals(Object left, Object right) {
        return compare(left, right).map(c -> c == 0).orElse(Objects.equals(left, right));
    }

    /**
     * Tests {@code left <operator> right}. A missing value, NaN or infinity, or incompatible types
     * give {@link Truth#UNKNOWN}, for every operator including {@code !=}.
     */
    public static Truth test(Object left, String operator, Object right) {
        Optional<Integer> comparison = compare(left, right);
        if (comparison.isEmpty()) {
            return Truth.UNKNOWN;
        }
        int c = comparison.get();
        return Truth.of(switch (operator) {
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            case "==" -> c == 0;
            case "!=" -> c != 0;
            default -> throw new IllegalArgumentException("Unknown operator: " + operator);
        });
    }

    /**
     * Extracts a time of day from a {@link LocalTime}, a date-time object, or an ISO-8601 string
     * (time, local date-time or offset date-time). Returns null if no time can be extracted.
     */
    public static LocalTime toLocalTime(Object value) {
        if (value instanceof LocalTime time) {
            return time;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalTime();
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toLocalTime();
        }
        if (value instanceof ZonedDateTime dateTime) {
            return dateTime.toLocalTime();
        }
        if (value instanceof String text) {
            try {
                return LocalTime.parse(text);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
            try {
                return LocalDateTime.parse(text).toLocalTime();
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
            try {
                return OffsetDateTime.parse(text).toLocalTime();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }
}
