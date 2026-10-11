package com.tradeevo.core.engine;

import static com.tradeevo.TestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tradeevo.TestSupport;
import com.tradeevo.core.data.DataFile;
import com.tradeevo.core.error.InvalidConfigException;
import com.tradeevo.core.rule.RuleConfig;
import com.tradeevo.core.strategy.RunMode;
import com.tradeevo.core.strategy.Strategy;
import com.tradeevo.core.strategy.StrategyClassRegistry;
import com.tradeevo.core.strategy.StrategyConfig;
import com.tradeevo.core.strategy.StrategyResult;
import com.tradeevo.core.strategy.StrategyRunContext;

class StrategyEngineTest {

    /** Records the context it receives and reports how many data files it saw. */
    static class RecordingStrategy implements Strategy {
        StrategyRunContext received;

        @Override
        public StrategyResult run(StrategyRunContext context) {
            received = context;
            return StrategyResult.of(Map.of("files", context.data().size()), json("{'ok': true}"));
        }

        @Override
        public List<String> validate(StrategyConfig config) {
            return config.settingsOrEmpty().has("bad") ? List.of("settings.bad: not allowed") : List.of();
        }
    }

    static class FailingStrategy implements Strategy {
        @Override
        public StrategyResult run(StrategyRunContext context) {
            throw new IllegalStateException("boom");
        }
    }

    private final RecordingStrategy recording = new RecordingStrategy();
    private final StrategyEngine engine = new StrategyEngine(
            new StrategyClassRegistry(List.of(recording, new FailingStrategy())),
            TestSupport.ruleFactory(),
            Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));

    private static StrategyConfig config(Class<?> strategyClass, String settings, RuleConfig... rules) {
        return new StrategyConfig("s1", 2, null, strategyClass.getName(), null,
                settings == null ? null : json(settings), List.of(rules));
    }

    private static final RuleConfig ENTRY =
            new RuleConfig("entry", "Compare", json("{'field': 'x', 'operator': '>', 'value': 1}"), "OPEN");

    @Test
    void runsTheStrategyWithBuiltRulesAndData() {
        List<DataFile> data = List.of(new DataFile(LocalDate.of(2024, 1, 2), json("{'a': 1}")),
                new DataFile(LocalDate.of(2024, 1, 3), json("{'a': 2}")));

        RunRecord record = engine.run(config(RecordingStrategy.class, "{'k': 1}", ENTRY), data, RunMode.BACKTEST,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(record.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(record.runId()).startsWith("20261010-120000-");
        assertThat(record.metrics()).containsEntry("files", 2);
        assertThat(record.details()).isEqualTo(json("{'ok': true}"));
        assertThat(record.dataDates()).containsExactly(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3));
        assertThat(record.strategy().version()).isEqualTo(2);

        assertThat(recording.received.mode()).isEqualTo(RunMode.BACKTEST);
        assertThat(recording.received.settings()).isEqualTo(json("{'k': 1}"));
        assertThat(recording.received.rules().get("entry")).isPresent();
        assertThat(recording.received.data()).isEqualTo(data);
    }

    @Test
    void collectsAllConfigErrorsBeforeRunning() {
        StrategyConfig bad = new StrategyConfig("../x", null, null, "com.example.Missing", "bad/dataset", json("[1]"),
                List.of(new RuleConfig("r", "Nope", null, null)));

        assertThat(engine.validate(bad)).containsExactly(
                "id: " + com.tradeevo.core.Identifiers.RULE,
                "dataset: " + com.tradeevo.core.Identifiers.RULE,
                "settings: must be an object",
                "strategyClass: unknown strategy class 'com.example.Missing' (available: ["
                        + FailingStrategy.class.getName() + ", " + RecordingStrategy.class.getName() + "])",
                "rules[0].rule: unknown rule type 'Nope'");
        assertThatThrownBy(() -> engine.run(bad, List.of(), RunMode.EVALUATION, null, null))
                .isInstanceOf(InvalidConfigException.class);
    }

    @Test
    void includesStrategySpecificValidation() {
        assertThat(engine.validate(config(RecordingStrategy.class, "{'bad': 1}"))).containsExactly("settings.bad: not allowed");
        assertThat(engine.validate(new StrategyConfig(null, null, null, null, null, null, null)))
                .containsExactly("id: required", "strategyClass: required");
    }

    @Test
    void anExceptionInTheStrategyBecomesAFailedRecord() {
        RunRecord record = engine.run(config(FailingStrategy.class, null), List.of(), RunMode.EVALUATION, null, null);

        assertThat(record.status()).isEqualTo(RunStatus.FAILED);
        assertThat(record.error()).isEqualTo("IllegalStateException: boom");
        assertThat(record.metrics()).isEmpty();
    }
}
