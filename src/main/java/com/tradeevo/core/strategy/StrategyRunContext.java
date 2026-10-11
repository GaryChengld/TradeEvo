package com.tradeevo.core.strategy;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.data.DataFile;
import com.tradeevo.core.rule.RuleSet;

/**
 * Everything a strategy receives for one run.
 *
 * @param data raw data in the strategy's own format: one entry per date file for a backtest, or a
 *             single entry with a null date for an evaluation
 */
public record StrategyRunContext(StrategyConfig config, RuleSet rules, List<DataFile> data, RunMode mode) {

    public StrategyRunContext {
        data = List.copyOf(data);
    }

    /** The config's free-form settings, or an empty object. */
    public JsonNode settings() {
        return config.settingsOrEmpty();
    }
}
