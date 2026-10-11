package com.tradeevo.core.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.json.CanonicalJson;

/**
 * What a strategy reports for a run.
 *
 * @param metrics     numeric results; runs are compared by these names
 * @param details     strategy-defined detail (signals, trades, diagnostics), stored as-is
 * @param warnings    non-fatal issues worth surfacing
 * @param assumptions execution assumptions behind the metrics (execution model, costs, sizing, units).
 *                    Recorded in the run manifest; runs with different assumptions are flagged as not
 *                    comparable.
 */
public record StrategyResult(Map<String, Number> metrics, JsonNode details, List<String> warnings, JsonNode assumptions) {

    public StrategyResult {
        metrics = metrics == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
        warnings = warnings == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(warnings));
        details = CanonicalJson.copy(details);
        assumptions = CanonicalJson.copy(assumptions);
    }

    public static StrategyResult of(Map<String, Number> metrics, JsonNode details) {
        return new StrategyResult(metrics, details, List.of(), null);
    }

    @Override
    public JsonNode details() {
        return CanonicalJson.copy(details);
    }

    @Override
    public JsonNode assumptions() {
        return CanonicalJson.copy(assumptions);
    }
}
