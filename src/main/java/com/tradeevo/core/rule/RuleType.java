package com.tradeevo.core.rule;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A reusable rule class that configs refer to by {@link #name()}. Implementations are Spring
 * beans and are registered automatically.
 */
public interface RuleType {

    /** The name used in configs, e.g. {@code "Compare"}. */
    String name();

    String description();

    ParamSchema schema();

    /**
     * Cross-parameter checks, run only after the params passed {@link #schema()}. {@code params}
     * already has defaults applied; {@code path} points at the params object.
     */
    default List<String> validateParams(JsonNode params, String path) {
        return List.of();
    }

    /**
     * Builds the rule. {@code params} is valid and has defaults applied. Nested rules are built
     * with {@code factory}.
     */
    Rule create(JsonNode params, RuleFactory factory);
}
