package com.tradeevo.core.engine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.strategy.RunMode;
import com.tradeevo.core.strategy.StrategyConfig;

/**
 * The full record of one run: the exact config used, the data dates, and the strategy's result.
 * {@code runId}, {@code startedAt} and {@code finishedAt} are metadata; everything else is
 * reproducible from the config and data.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunRecord(
        String runId,
        RunStatus status,
        RunMode mode,
        StrategyConfig strategy,
        LocalDate startDate,
        LocalDate endDate,
        List<LocalDate> dataDates,
        Map<String, Number> metrics,
        JsonNode details,
        List<String> warnings,
        String error,
        Instant startedAt,
        Instant finishedAt) {
}
