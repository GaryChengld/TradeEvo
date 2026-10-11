package com.tradeevo.core.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.tradeevo.core.json.CanonicalJson;
import com.tradeevo.core.rule.RuleConfig;

/**
 * One version of a strategy, as stored in its JSON config file.
 *
 * @param id            strategy id; also the storage directory name
 * @param version       assigned by the store (1, 2, ...); ignored on input
 * @param strategyClass fully qualified name of the {@link Strategy} class to run
 * @param dataset       data folder under the data dir; defaults to {@code id}
 * @param settings      free-form, strategy-specific settings
 * @param rules         named rules built from the core rule library
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StrategyConfig(
        String id,
        Integer version,
        String description,
        String strategyClass,
        String dataset,
        JsonNode settings,
        List<RuleConfig> rules) {

    public StrategyConfig {
        // Null elements are kept so validation can report them by index.
        rules = rules == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(rules));
        settings = CanonicalJson.copy(settings);
    }

    /** A copy: the stored settings cannot be changed through the returned node. */
    @Override
    public JsonNode settings() {
        return CanonicalJson.copy(settings);
    }

    @JsonIgnore
    public String effectiveDataset() {
        return dataset != null && !dataset.isBlank() ? dataset : id;
    }

    @JsonIgnore
    public JsonNode settingsOrEmpty() {
        return settings != null && !settings.isNull() ? settings.deepCopy() : JsonNodeFactory.instance.objectNode();
    }

    public StrategyConfig withIdAndVersion(String newId, Integer newVersion) {
        return new StrategyConfig(newId, newVersion, description, strategyClass, dataset, settings, rules);
    }
}
