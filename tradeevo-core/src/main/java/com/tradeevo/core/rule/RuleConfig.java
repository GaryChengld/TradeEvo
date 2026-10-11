package com.tradeevo.core.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.json.CanonicalJson;

/**
 * One named rule in a strategy config.
 *
 * @param name      unique within the config; the strategy class looks rules up by this name
 * @param rule      the registered {@link RuleType} name
 * @param params    parameters for the rule type
 * @param action    optional label the strategy class interprets (e.g. {@code OPEN}); opaque to the core
 * @param onUnknown what to do when the rule cannot be decided; null means {@link UnknownPolicy#NO_MATCH}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleConfig(String name, String rule, JsonNode params, String action, UnknownPolicy onUnknown) {

    @JsonCreator
    public RuleConfig {
        params = CanonicalJson.copy(params);
    }

    public RuleConfig(String name, String rule, JsonNode params, String action) {
        this(name, rule, params, action, null);
    }

    @Override
    public JsonNode params() {
        return CanonicalJson.copy(params);
    }

    /** The configured policy, or the default. */
    public UnknownPolicy effectiveOnUnknown() {
        return onUnknown != null ? onUnknown : UnknownPolicy.NO_MATCH;
    }
}
