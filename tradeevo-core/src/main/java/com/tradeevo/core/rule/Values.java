package com.tradeevo.core.rule;

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
     * Compares two values of compatible types: numbers numerically, otherwise values of the same
     * {@link Comparable} type. Returns empty when the values cannot be compared.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional<Integer> compare(Object left, Object right) {
        if (left == null || right == null) {
            return Optional.empty();
        }
        if (left instanceof Number l && right instanceof Number r) {
            return Optional.of(Double.compare(l.doubleValue(), r.doubleValue()));
        }
        if (left.getClass() == right.getClass() && left instanceof Comparable comparable) {
            return Optional.of(Integer.signum(comparable.compareTo(right)));
        }
        return Optional.empty();
    }

    /** Equality that treats numbers of different Java types (e.g. 1 and 1.0) as equal. */
    public static boolean looseEquals(Object left, Object right) {
        return compare(left, right).map(c -> c == 0).orElse(Objects.equals(left, right));
    }

    /**
     * Tests {@code left <operator> right}. A missing value or incompatible types give
     * {@link Truth#UNKNOWN}, for every operator including {@code !=}.
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
