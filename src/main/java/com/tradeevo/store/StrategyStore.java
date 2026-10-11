package com.tradeevo.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeevo.core.Identifiers;
import com.tradeevo.core.error.ConflictException;
import com.tradeevo.core.error.NotFoundException;
import com.tradeevo.core.strategy.StrategyConfig;

/**
 * Stores strategy versions as {@code {storeDir}/strategies/{id}/v{n}.json}. Versions are immutable:
 * a change is always saved as a new version.
 */
public class StrategyStore {

    private static final Pattern VERSION_FILE = Pattern.compile("v(\\d+)\\.json");

    private final Path root;
    private final ObjectMapper mapper;

    public StrategyStore(Path storeDir, ObjectMapper mapper) {
        this.root = storeDir.resolve("strategies");
        this.mapper = mapper;
    }

    /** Saves a new strategy as version 1. Any version in the config is ignored. */
    public synchronized StrategyConfig create(StrategyConfig config) {
        if (!versions(config.id()).isEmpty()) {
            throw new ConflictException("Strategy '" + config.id() + "' already exists; add a new version instead");
        }
        return write(config.withIdAndVersion(config.id(), 1));
    }

    /** Saves the config as the next version of an existing strategy. */
    public synchronized StrategyConfig addVersion(String id, StrategyConfig config) {
        int next = latestVersion(id) + 1;
        return write(config.withIdAndVersion(id, next));
    }

    public StrategyConfig get(String id, int version) {
        Path file = directory(id).resolve("v" + version + ".json");
        if (!Files.isRegularFile(file)) {
            latestVersion(id); // "not found" when the strategy itself is missing
            throw new NotFoundException("Strategy '" + id + "' has no version " + version);
        }
        return JsonFiles.read(mapper, file, StrategyConfig.class);
    }

    public StrategyConfig latest(String id) {
        return get(id, latestVersion(id));
    }

    /** @throws NotFoundException if the strategy does not exist */
    public int latestVersion(String id) {
        List<Integer> versions = versions(id);
        if (versions.isEmpty()) {
            throw new NotFoundException("Strategy '" + id + "' not found");
        }
        return versions.getLast();
    }

    /** Version numbers in ascending order; empty if the strategy does not exist. */
    public List<Integer> versions(String id) {
        Path directory = directory(id);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> VERSION_FILE.matcher(file.getFileName().toString()))
                    .filter(Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<StrategySummary> list() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<StrategySummary> summaries = new ArrayList<>();
        try (Stream<Path> directories = Files.list(root)) {
            for (Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
                String id = directory.getFileName().toString();
                List<Integer> versions = versions(id);
                if (!versions.isEmpty()) {
                    StrategyConfig latest = get(id, versions.getLast());
                    summaries.add(new StrategySummary(id, versions.getLast(), versions,
                            latest.description(), latest.strategyClass()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return summaries;
    }

    private StrategyConfig write(StrategyConfig config) {
        Path file = directory(config.id()).resolve("v" + config.version() + ".json");
        try {
            JsonFiles.writeNew(mapper, file, config);
        } catch (FileAlreadyExistsException e) {
            throw new ConflictException("Strategy '" + config.id() + "' version " + config.version() + " already exists");
        }
        return config;
    }

    private Path directory(String id) {
        if (!Identifiers.isValid(id)) {
            throw new NotFoundException("Strategy '" + id + "' not found");
        }
        return root.resolve(id);
    }
}
