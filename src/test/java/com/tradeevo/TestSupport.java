package com.tradeevo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tradeevo.core.rule.RuleFactory;
import com.tradeevo.core.rule.RuleRegistry;
import com.tradeevo.core.rule.types.AllRule;
import com.tradeevo.core.rule.types.AnyRule;
import com.tradeevo.core.rule.types.BetweenRule;
import com.tradeevo.core.rule.types.CompareRule;
import com.tradeevo.core.rule.types.InRule;
import com.tradeevo.core.rule.types.NotRule;
import com.tradeevo.core.rule.types.TimeWindowRule;
import com.tradeevo.core.strategy.StrategyConfig;

/** Shared helpers for tests that run without a Spring context. */
public final class TestSupport {

    public static final Path EXAMPLE_CONFIG = Path.of("examples", "threshold-signal.json");
    public static final Path EXAMPLE_DATA_DIR = Path.of("examples", "data");

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private TestSupport() {
    }

    public static RuleFactory ruleFactory() {
        return new RuleFactory(new RuleRegistry(List.of(new CompareRule(), new BetweenRule(), new InRule(),
                new TimeWindowRule(), new AllRule(), new AnyRule(), new NotRule())));
    }

    /** Parses JSON written with single quotes, for readable test literals. */
    public static JsonNode json(String singleQuoted) {
        try {
            return MAPPER.readTree(singleQuoted.replace('\'', '"'));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static StrategyConfig exampleConfig() {
        try {
            return MAPPER.readValue(EXAMPLE_CONFIG.toFile(), StrategyConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
