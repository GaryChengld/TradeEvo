package com.tradeevo.core.rule;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * One named rule in a strategy config.
 *
 * @param name   unique within the config; the strategy class looks rules up by this name
 * @param rule   the registered {@link RuleType} name
 * @param params parameters for the rule type
 * @param action optional label the strategy class interprets (e.g. {@code OPEN}); opaque to the core
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleConfig(String name, String rule, JsonNode params, String action) {
}
