package com.tradeevo.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.tradeevo.core.engine.RunStatus;

/**
 * Metrics of several runs side by side.
 *
 * @param metricNames union of metric names across the runs, sorted
 */
public record RunComparison(List<String> metricNames, List<ComparedRun> runs) {

    public record ComparedRun(
            String runId,
            String strategyId,
            Integer version,
            RunStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, Number> metrics) {
    }
}
