package com.tradeevo.core.data;

import java.time.LocalDate;
import java.util.List;

/**
 * Which dates of a requested range had no session data.
 *
 * @param closedDates  dates whose file marks a non-trading day ({@code {"session": "closed"}})
 * @param missingDates dates with no file at all; skipped, but reported because an unmarked gap may be
 *                     an accidentally missing trading day
 */
public record DataCoverage(List<LocalDate> closedDates, List<LocalDate> missingDates) {

    /** No range was loaded (e.g. data supplied in an evaluation request). */
    public static final DataCoverage NONE = new DataCoverage(List.of(), List.of());

    public DataCoverage {
        closedDates = closedDates == null ? List.of() : List.copyOf(closedDates);
        missingDates = missingDates == null ? List.of() : List.copyOf(missingDates);
    }
}
