package com.tradeevo.store;

import static com.tradeevo.TestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tradeevo.TestSupport;
import com.tradeevo.core.engine.RunRecord;
import com.tradeevo.core.engine.RunStatus;
import com.tradeevo.core.error.ConflictException;
import com.tradeevo.core.error.NotFoundException;
import com.tradeevo.core.strategy.RunMode;
import com.tradeevo.core.strategy.StrategyConfig;

class StoreTest {

    @TempDir
    Path storeDir;

    private static StrategyConfig config(String id, String description) {
        return new StrategyConfig(id, 99, description, "com.example.S", null, json("{'k': 1}"), List.of());
    }

    @Test
    void strategyVersionsAreNumberedAndImmutable() {
        StrategyStore store = new StrategyStore(storeDir, TestSupport.MAPPER);

        assertThat(store.create(config("s", "first")).version()).as("input version is ignored").isEqualTo(1);
        assertThat(store.addVersion("s", config("s", "second")).version()).isEqualTo(2);
        assertThat(store.addVersion("s", config("s", "third")).version()).isEqualTo(3);

        assertThat(store.versions("s")).containsExactly(1, 2, 3);
        assertThat(store.get("s", 1).description()).isEqualTo("first");
        assertThat(store.latest("s").description()).isEqualTo("third");
        assertThat(store.get("s", 2).settings()).isEqualTo(json("{'k': 1}"));
        assertThat(store.list()).containsExactly(new StrategySummary("s", 3, List.of(1, 2, 3), "third", "com.example.S"));

        assertThatThrownBy(() -> store.create(config("s", "again"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void missingStrategiesAreNotFound() {
        StrategyStore store = new StrategyStore(storeDir, TestSupport.MAPPER);
        store.create(config("s", null));

        assertThatThrownBy(() -> store.latest("nope")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.get("s", 7)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.addVersion("nope", config("nope", null))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.latest("../s")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void runsRoundTripAndFilterByStrategy() {
        RunStore store = new RunStore(storeDir, TestSupport.MAPPER);
        RunRecord a = run("run-a", "s1", "2026-01-01T00:00:00Z");
        RunRecord b = run("run-b", "s2", "2026-01-02T00:00:00Z");
        RunRecord c = run("run-c", "s1", "2026-01-03T00:00:00Z");
        store.save(c);
        store.save(a);
        store.save(b);

        RunRecord loaded = store.get("run-a");
        assertThat(loaded.metrics()).containsEntry("count", 3).containsEntry("ratio", 0.5);
        assertThat(loaded.details()).isEqualTo(a.details());
        assertThat(loaded.dataDates()).isEqualTo(a.dataDates());
        assertThat(store.list(null)).extracting(RunRecord::runId).containsExactly("run-a", "run-b", "run-c");
        assertThat(store.list("s1")).extracting(RunRecord::runId).containsExactly("run-a", "run-c");
        assertThatThrownBy(() -> store.save(a)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> store.get("missing")).isInstanceOf(NotFoundException.class);
    }

    private static RunRecord run(String runId, String strategyId, String startedAt) {
        return new RunRecord(runId, RunStatus.COMPLETED, RunMode.BACKTEST, config(strategyId, null),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), List.of(LocalDate.of(2024, 1, 2)),
                Map.of("count", 3, "ratio", 0.5), json("{'signals': [1, 2]}"), List.of(), null,
                Instant.parse(startedAt), Instant.parse(startedAt));
    }
}
