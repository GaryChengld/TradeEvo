package com.tradeevo.core.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * The build of the engine that produced a run: Maven version plus git commit, written into
 * {@code engine-version.properties} at build time. Values that were not filled in (e.g. when run
 * from an IDE without a Maven build) are {@code "unknown"}.
 *
 * @param dirty whether the build had uncommitted changes, so the commit does not identify the code
 */
public record EngineVersion(String version, String commit, boolean dirty) {

    private static final String UNKNOWN = "unknown";
    private static final EngineVersion CURRENT = load();

    public static EngineVersion current() {
        return CURRENT;
    }

    private static EngineVersion load() {
        Properties properties = new Properties();
        try (InputStream in = EngineVersion.class.getResourceAsStream("/com/tradeevo/core/engine-version.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new EngineVersion(value(properties, "version"), value(properties, "commit"),
                Boolean.parseBoolean(value(properties, "dirty")));
    }

    private static String value(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() || value.contains("@") || value.contains("${") ? UNKNOWN : value.trim();
    }
}
