package com.tradeevo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tradeevo.core.strategy.StrategyConfig;

/** Shared helpers for app tests. Example files live in the repository's {@code examples/} directory. */
public final class AppTestSupport {

    /** Set by surefire (see the parent pom); falls back to the layout when running from the module dir. */
    public static final Path EXAMPLES = Path.of(System.getProperty("tradeevo.examples", "../examples")).toAbsolutePath();
    public static final Path EXAMPLE_CONFIG = EXAMPLES.resolve("threshold-signal.json");
    public static final Path EXAMPLE_DATA_DIR = EXAMPLES.resolve("data");

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private AppTestSupport() {
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
