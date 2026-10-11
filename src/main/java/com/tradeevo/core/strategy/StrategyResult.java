package com.tradeevo.core.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * What a strategy reports for a run.
 *
 * @param metrics  numeric results; runs are compared by these names
 * @param details  strategy-defined detail (signals, trades, diagnostics), stored as-is
 * @param warnings non-fatal issues worth surfacing
 */
public record StrategyResult(Map<String, Number> metrics, JsonNode details, List<String> warnings) {

    public StrategyResult {
        metrics = metrics == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
        warnings = warnings == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public static StrategyResult of(Map<String, Number> metrics, JsonNode details) {
        return new StrategyResult(metrics, details, List.of());
    }
}
