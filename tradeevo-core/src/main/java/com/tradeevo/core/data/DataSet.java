package com.tradeevo.core.data;

import java.util.List;

/**
 * The session files of a date range, in date order, plus the range's coverage.
 *
 * @param files files with session data; closed-day markers are not included
 */
public record DataSet(List<DataFile> files, DataCoverage coverage) {

    public DataSet {
        files = List.copyOf(files);
    }
}
