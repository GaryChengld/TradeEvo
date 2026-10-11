package com.tradeevo.core.engine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.json.CanonicalJson;
import com.tradeevo.core.rule.RuleStats;
import com.tradeevo.core.strategy.RunMode;
import com.tradeevo.core.strategy.StrategyConfig;

/**
 * The full record of one run: the exact config used, the data dates, the strategy's result, and a
 * manifest fingerprinting engine, strategy and data. {@code runId}, {@code startedAt} and
 * {@code finishedAt} are metadata; everything else is reproducible from the config and data.
 *
 * @param ruleStats outcome counts per named rule
 * @param manifest  null only for records written before manifests existed
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
        Instant finishedAt,
        Map<String, RuleStats> ruleStats,
        RunManifest manifest) {

    public RunRecord {
        dataDates = dataDates == null ? List.of() : List.copyOf(dataDates);
        metrics = metrics == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        ruleStats = ruleStats == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(ruleStats));
        details = CanonicalJson.copy(details);
    }

    @Override
    public JsonNode details() {
        return CanonicalJson.copy(details);
    }
}
