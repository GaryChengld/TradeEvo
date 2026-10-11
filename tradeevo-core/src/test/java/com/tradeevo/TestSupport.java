package com.tradeevo;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tradeevo.core.rule.RuleFactory;
import com.tradeevo.core.rule.RuleRegistry;
import com.tradeevo.core.rule.types.CoreRuleTypes;

/** Shared helpers for core tests. */
public final class TestSupport {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private TestSupport() {
    }

    public static RuleFactory ruleFactory() {
        return new RuleFactory(new RuleRegistry(CoreRuleTypes.builtIns()));
    }

    /** Parses JSON written with single quotes, for readable test literals. */
    public static JsonNode json(String singleQuoted) {
        try {
            return MAPPER.readTree(singleQuoted.replace('\'', '"'));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
