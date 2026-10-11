package com.tradeevo.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeevo.core.Identifiers;
import com.tradeevo.core.engine.RunRecord;
import com.tradeevo.core.error.ConflictException;
import com.tradeevo.core.error.NotFoundException;

/** Stores run records as {@code {storeDir}/runs/{runId}/result.json}. */
public class RunStore {

    private static final String RESULT_FILE = "result.json";

    private final Path root;
    private final ObjectMapper mapper;

    public RunStore(Path storeDir, ObjectMapper mapper) {
        this.root = storeDir.resolve("runs");
        this.mapper = mapper;
    }

    public RunRecord save(RunRecord record) {
        try {
            JsonFiles.writeNew(mapper, directory(record.runId()).resolve(RESULT_FILE), record);
        } catch (FileAlreadyExistsException e) {
            throw new ConflictException("Run '" + record.runId() + "' already exists");
        }
        return record;
    }

    public RunRecord get(String runId) {
        Path file = directory(runId).resolve(RESULT_FILE);
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("Run '" + runId + "' not found");
        }
        return JsonFiles.read(mapper, file, RunRecord.class);
    }

    /** All runs, oldest first, optionally only those of one strategy. */
    public List<RunRecord> list(String strategyId) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> directories = Files.list(root)) {
            return directories
                    .map(directory -> directory.resolve(RESULT_FILE))
                    .filter(Files::isRegularFile)
                    .map(file -> JsonFiles.read(mapper, file, RunRecord.class))
                    .filter(run -> strategyId == null || strategyId.equals(run.strategy().id()))
                    .sorted(Comparator.comparing(RunRecord::startedAt).thenComparing(RunRecord::runId))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path directory(String runId) {
        if (!Identifiers.isValid(runId)) {
            throw new NotFoundException("Run '" + runId + "' not found");
        }
        return root.resolve(runId);
    }
}
