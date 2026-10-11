package com.tradeevo.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeevo.core.strategy.StrategyConfig;
import com.tradeevo.service.StrategyService;
import com.tradeevo.store.StrategySummary;

/** Strategy configs and their immutable versions. */
@RestController
@RequestMapping("/api/strategies")
public class StrategyController {

    private final StrategyService strategies;

    public StrategyController(StrategyService strategies) {
        this.strategies = strategies;
    }

    /** Creates a strategy as version 1. */
    @PostMapping
    public ResponseEntity<StrategyConfig> create(@RequestBody StrategyConfig config) {
        StrategyConfig created = strategies.create(config);
        return ResponseEntity.created(location(created)).body(created);
    }

    /** Saves a changed config as the next version. */
    @PostMapping("/{id}/versions")
    public ResponseEntity<StrategyConfig> addVersion(@PathVariable String id, @RequestBody StrategyConfig config) {
        StrategyConfig created = strategies.addVersion(id, config);
        return ResponseEntity.created(location(created)).body(created);
    }

    @GetMapping
    public List<StrategySummary> list() {
        return strategies.list();
    }

    /** The latest version. */
    @GetMapping("/{id}")
    public StrategyConfig latest(@PathVariable String id) {
        return strategies.get(id, null);
    }

    @GetMapping("/{id}/versions")
    public List<Integer> versions(@PathVariable String id) {
        return strategies.versions(id);
    }

    @GetMapping("/{id}/versions/{version}")
    public StrategyConfig version(@PathVariable String id, @PathVariable int version) {
        return strategies.get(id, version);
    }

    private static URI location(StrategyConfig config) {
        return URI.create("/api/strategies/" + config.id() + "/versions/" + config.version());
    }
}
