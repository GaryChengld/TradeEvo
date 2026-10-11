package com.tradeevo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tradeevo.core.engine.StrategyEngine;
import com.tradeevo.core.error.BadRequestException;
import com.tradeevo.core.strategy.StrategyConfig;
import com.tradeevo.store.StrategyStore;
import com.tradeevo.store.StrategySummary;

/** Creates and reads strategy versions. Only valid configs are stored. */
@Service
public class StrategyService {

    private final StrategyStore store;
    private final StrategyEngine engine;

    public StrategyService(StrategyStore store, StrategyEngine engine) {
        this.store = store;
        this.engine = engine;
    }

    public StrategyConfig create(StrategyConfig config) {
        if (config == null) {
            throw new BadRequestException("strategy config is required");
        }
        engine.requireValid(config);
        return store.create(config);
    }

    public StrategyConfig addVersion(String id, StrategyConfig config) {
        if (config == null) {
            throw new BadRequestException("strategy config is required");
        }
        if (config.id() != null && !config.id().equals(id)) {
            throw new BadRequestException("id in body ('" + config.id() + "') does not match path ('" + id + "')");
        }
        StrategyConfig withId = config.withIdAndVersion(id, null);
        store.latestVersion(id); // 404 before validation if the strategy does not exist
        engine.requireValid(withId);
        return store.addVersion(id, withId);
    }

    /** The given version, or the latest when {@code version} is null. */
    public StrategyConfig get(String id, Integer version) {
        return version == null ? store.latest(id) : store.get(id, version);
    }

    public List<Integer> versions(String id) {
        store.latestVersion(id); // 404 if missing
        return store.versions(id);
    }

    public List<StrategySummary> list() {
        return store.list();
    }
}
