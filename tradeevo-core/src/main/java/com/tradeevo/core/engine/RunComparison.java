package com.tradeevo.core.engine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metrics of several runs side by side, with a verdict on whether comparing them is meaningful.
 *
 * @param metricNames union of metric names across the runs, sorted
 * @param comparable  true when no {@code issues} were found
 * @param issues      each difference from the baseline (the first run) that makes the runs not comparable
 */
public record RunComparison(List<String> metricNames, boolean comparable, List<Issue> issues, List<ComparedRun> runs) {

    public RunComparison {
        metricNames = List.copyOf(metricNames);
        issues = List.copyOf(issues);
        runs = List.copyOf(runs);
    }

    public record ComparedRun(
            String runId,
            String strategyId,
            Integer version,
            RunStatus status,
            LocalDate startDate,
            LocalDate endDate,
            String dataHash,
            Map<String, Number> metrics) {
    }

    /** {@code field} of run {@code runId} differs from the baseline run. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Issue(String runId, String field, String baseline, String actual) {
    }
}
