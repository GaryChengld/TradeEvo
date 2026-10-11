package com.tradeevo.core.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradeevo.core.data.DataFile;
import com.tradeevo.core.json.CanonicalJson;
import com.tradeevo.core.strategy.StrategyConfig;

/**
 * Fingerprints of everything that determines a run's result. Two runs with equal manifests used the
 * same engine build, the same strategy behavior and the same input data.
 *
 * @param strategyHash SHA-256 of the behavior-relevant config: strategyClass, dataset, settings, rules
 *                     (not id, version or description)
 * @param dataHash     SHA-256 over the ordered {@code dataFiles} entries
 * @param dataFiles    SHA-256 of each input file's canonical JSON; date is null for request data
 * @param assumptions  execution assumptions declared by the strategy in its result
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunManifest(
        String engineVersion,
        String engineCommit,
        boolean engineDirty,
        String strategyClass,
        String strategyHash,
        String dataset,
        String dataHash,
        List<DataFileHash> dataFiles,
        JsonNode assumptions) {

    public record DataFileHash(LocalDate date, String sha256) {
    }

    public RunManifest {
        dataFiles = dataFiles == null ? List.of() : List.copyOf(dataFiles);
        assumptions = CanonicalJson.copy(assumptions);
    }

    @Override
    public JsonNode assumptions() {
        return CanonicalJson.copy(assumptions);
    }

    /** Fingerprints the config and data; call before the strategy runs. */
    public static RunManifest of(EngineVersion engine, StrategyConfig config, List<DataFile> data) {
        List<DataFileHash> files = new ArrayList<>(data.size());
        StringBuilder combined = new StringBuilder();
        for (DataFile file : data) {
            String hash = CanonicalJson.sha256(file.content());
            files.add(new DataFileHash(file.date(), hash));
            combined.append(file.date()).append(':').append(hash).append('\n');
        }
        return new RunManifest(engine.version(), engine.commit(), engine.dirty(), config.strategyClass(),
                strategyHash(config), config.effectiveDataset(), CanonicalJson.sha256(combined.toString()), files, null);
    }

    public static String strategyHash(StrategyConfig config) {
        ObjectNode behavior = JsonNodeFactory.instance.objectNode();
        behavior.put("strategyClass", config.strategyClass());
        behavior.put("dataset", config.effectiveDataset());
        behavior.set("settings", config.settingsOrEmpty());
        behavior.set("rules", CanonicalJson.toTree(config.rules()));
        return CanonicalJson.sha256(behavior);
    }

    public RunManifest withAssumptions(JsonNode newAssumptions) {
        return new RunManifest(engineVersion, engineCommit, engineDirty, strategyClass, strategyHash, dataset,
                dataHash, dataFiles, newAssumptions);
    }
}
