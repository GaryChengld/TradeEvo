package com.tradeevo.service;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeevo.core.error.BadRequestException;
import com.tradeevo.core.strategy.StrategyConfig;

/**
 * Turns the {@code strategy} part of a request into a config: either a reference to a stored
 * version, {@code {"strategyId": "x", "version": 2}} (latest when version is omitted), or an inline
 * config containing {@code strategyClass}.
 */
@Component
public class StrategyResolver {

    /** Id given to inline configs that do not set one. */
    public static final String INLINE_ID = "inline";

    private final StrategyService strategies;
    private final ObjectMapper mapper;

    public StrategyResolver(StrategyService strategies, ObjectMapper mapper) {
        this.strategies = strategies;
        this.mapper = mapper;
    }

    public StrategyConfig resolve(JsonNode strategy) {
        if (strategy == null || !strategy.isObject()) {
            throw new BadRequestException(
                    "strategy: required; either {\"strategyId\": ..., \"version\": ...} or an inline config with strategyClass");
        }
        if (strategy.has("strategyClass")) {
            StrategyConfig config;
            try {
                config = mapper.treeToValue(strategy, StrategyConfig.class);
            } catch (JsonProcessingException e) {
                throw new BadRequestException("strategy: invalid inline config: " + e.getOriginalMessage(), e);
            }
            return config.id() == null ? config.withIdAndVersion(INLINE_ID, config.version()) : config;
        }
        JsonNode id = strategy.get("strategyId");
        if (id == null || !id.isTextual()) {
            throw new BadRequestException(
                    "strategy: must contain either strategyId (a stored strategy) or strategyClass (an inline config)");
        }
        JsonNode version = strategy.get("version");
        if (version != null && !version.isNull() && !version.canConvertToExactIntegral()) {
            throw new BadRequestException("strategy.version: must be an integer");
        }
        return strategies.get(id.textValue(), version == null || version.isNull() ? null : version.intValue());
    }
}
