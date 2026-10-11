package com.tradeevo.core.data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

/**
 * Checks that record timestamps strictly increase across a whole run, including across file
 * boundaries. Strategies feed each record's time through one instance, in processing order.
 * Out-of-order or duplicate data is rejected, never reordered.
 *
 * <p>Accepted times: ISO-8601 local date-times ({@code 2024-01-02T09:30:00}) or {@link LocalDateTime};
 * offset date-times ({@code 2024-01-02T09:30:00-05:00}, {@code ...Z}) or {@link OffsetDateTime},
 * {@link ZonedDateTime}, {@link Instant}, compared as instants. A run must not mix local and offset times.
 */
public final class TimeOrder {

    private Comparable<?> previous;
    private Object previousTime;
    private String previousLocation;

    /**
     * Accepts the next record's time.
     *
     * @param location where the record is, for messages, e.g. {@code "2024-01-03 records[1]"}
     * @throws InvalidRecordOrderException if the time is missing, unparseable, of a different kind than
     *                                     earlier times, or not after the previous time
     */
    public void accept(Object time, String location) {
        Comparable<?> current = parse(time, location);
        if (previous != null) {
            if (previous.getClass() != current.getClass()) {
                throw new InvalidRecordOrderException(location + ": time " + time + " mixes local and offset times ("
                        + previousLocation + " has " + previousTime + ")");
            }
            int c = compare(current, previous);
            if (c == 0) {
                throw new InvalidRecordOrderException(location + ": duplicate timestamp " + time
                        + " (same as " + previousLocation + ")");
            }
            if (c < 0) {
                throw new InvalidRecordOrderException(location + ": time " + time + " is not after "
                        + previousLocation + " (" + previousTime + ")");
            }
        }
        previous = current;
        previousTime = time;
        previousLocation = location;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Comparable a, Comparable b) {
        return a.compareTo(b);
    }

    private static Comparable<?> parse(Object time, String location) {
        if (time instanceof LocalDateTime local) {
            return local;
        }
        if (time instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (time instanceof ZonedDateTime zoned) {
            return zoned.toInstant();
        }
        if (time instanceof Instant instant) {
            return instant;
        }
        if (time instanceof String text) {
            try {
                return LocalDateTime.parse(text);
            } catch (DateTimeParseException ignored) {
                // try an offset date-time next
            }
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException ignored) {
                // fall through to the error
            }
        }
        throw new InvalidRecordOrderException(location + ": time " + (time == null ? "is missing" : "'" + time
                + "' is not an ISO-8601 date-time"));
    }
}
