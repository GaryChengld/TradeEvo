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

    @Test
    void manifestFingerprintsStrategyAndData() {
        List<DataFile> data = List.of(new DataFile(LocalDate.of(2024, 1, 2), json("{'a': 1, 'b': [1, 2]}")),
                new DataFile(LocalDate.of(2024, 1, 3), json("{'a': 2}")));
        StrategyConfig config = config(RecordingStrategy.class, "{'k': 1}", ENTRY);

        RunManifest first = engine.run(config, data, RunMode.BACKTEST, null, null).manifest();
        RunManifest reordered = engine.run(config, List.of(
                new DataFile(LocalDate.of(2024, 1, 2), json("{'b': [1, 2], 'a': 1}")), data.get(1)),
                RunMode.BACKTEST, null, null).manifest();
        RunManifest changedData = engine.run(config, List.of(
                new DataFile(LocalDate.of(2024, 1, 2), json("{'a': 1, 'b': [2, 1]}")), data.get(1)),
                RunMode.BACKTEST, null, null).manifest();
        StrategyConfig renamed = new StrategyConfig("s1", 7, "new description", config.strategyClass(), "s1",
                config.settings(), config.rules());
        StrategyConfig changedRule = config(RecordingStrategy.class, "{'k': 1}",
                new RuleConfig("entry", "Compare", json("{'field': 'x', 'operator': '>', 'value': 2}"), "OPEN"));

        assertThat(first.strategyClass()).isEqualTo(RecordingStrategy.class.getName());
        assertThat(first.dataset()).isEqualTo("s1");
        assertThat(first.dataFiles()).hasSize(2);
        assertThat(first.engineVersion()).isNotBlank();
        assertThat(reordered).as("key order does not matter").isEqualTo(first);
        assertThat(changedData.dataFiles().get(0).sha256()).isNotEqualTo(first.dataFiles().get(0).sha256());
        assertThat(changedData.dataFiles().get(1)).isEqualTo(first.dataFiles().get(1));
        assertThat(changedData.dataHash()).isNotEqualTo(first.dataHash());
        assertThat(RunManifest.strategyHash(renamed)).as("id, version, description excluded").isEqualTo(first.strategyHash());
        assertThat(RunManifest.strategyHash(changedRule)).isNotEqualTo(first.strategyHash());
    }

    @Test
    void manifestIsTakenBeforeTheStrategyCanMutateItsInput() {
        Strategy mutating = new Strategy() {
            @Override
            public StrategyResult run(StrategyRunContext context) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) context.data().getFirst().content()).put("a", 99);
                return new StrategyResult(Map.of(), null, List.of(), json("{'costs': 'none'}"));
            }
        };
        StrategyEngine engine = StrategyEngine.create(List.of(mutating));
        DataFile file = new DataFile(LocalDate.of(2024, 1, 2), json("{'a': 1}"));
        String expected = com.tradeevo.core.json.CanonicalJson.sha256(json("{'a': 1}"));

        RunRecord record = engine.run(new StrategyConfig("m", null, null, mutating.getClass().getName(), null, null, List.of()),
                List.of(file), RunMode.BACKTEST, null, null);

        assertThat(record.manifest().dataFiles().getFirst().sha256()).isEqualTo(expected);
        assertThat(record.manifest().assumptions()).isEqualTo(json("{'costs': 'none'}"));
    }

    @Test
    void reportsRuleStatsAndWarnsAboutUnknowns() {
        Strategy evaluating = new Strategy() {
            @Override
            public StrategyResult run(StrategyRunContext context) {
                context.rules().matches("entry", com.tradeevo.core.rule.Facts.of(Map.of("x", 5)));
                context.rules().matches("entry", com.tradeevo.core.rule.Facts.of(Map.of()));
                return StrategyResult.of(Map.of(), null);
            }
        };
        StrategyEngine engine = StrategyEngine.create(List.of(evaluating));

        RunRecord record = engine.run(new StrategyConfig("e", null, null, evaluating.getClass().getName(), null, null,
                List.of(ENTRY)), List.of(), RunMode.EVALUATION, null, null);

        assertThat(record.ruleStats()).containsEntry("entry", new com.tradeevo.core.rule.RuleStats(1, 0, 1));
        assertThat(record.warnings()).containsExactly(
                "rule 'entry' was UNKNOWN for 1 of 2 evaluations (missing or incompatible facts)");
    }

    @Test
    void anUndecidableFailRuleFailsTheRun() {
        Strategy evaluating = new Strategy() {
            @Override
            public StrategyResult run(StrategyRunContext context) {
                context.rules().matches("entry", com.tradeevo.core.rule.Facts.of(Map.of()));
                return StrategyResult.of(Map.of(), null);
            }
        };
        RuleConfig strict = new RuleConfig("entry", "Compare", json("{'field': 'x', 'operator': '>', 'value': 1}"),
                "OPEN", com.tradeevo.core.rule.UnknownPolicy.FAIL);

        RunRecord record = StrategyEngine.create(List.of(evaluating)).run(new StrategyConfig("f", null, null,
                evaluating.getClass().getName(), null, null, List.of(strict)), List.of(), RunMode.EVALUATION, null, null);

        assertThat(record.status()).isEqualTo(RunStatus.FAILED);
        assertThat(record.error()).isEqualTo(
                "UnknownFactsException: rule 'entry' is UNKNOWN (onUnknown=FAIL): missing facts [x]");
        assertThat(record.ruleStats()).containsEntry("entry", new com.tradeevo.core.rule.RuleStats(0, 0, 1));
    }

    @Test
    void coverageGoesIntoTheManifestAndMissingDatesIntoWarnings() {
        StrategyConfig config = config(RecordingStrategy.class, null);
        List<DataFile> files = List.of(new DataFile(LocalDate.of(2024, 1, 2), json("{'a': 1}")));
        com.tradeevo.core.data.DataCoverage none = com.tradeevo.core.data.DataCoverage.NONE;
        com.tradeevo.core.data.DataCoverage gaps = new com.tradeevo.core.data.DataCoverage(
                List.of(LocalDate.of(2024, 1, 6)), List.of(LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 4)));

        RunRecord complete = engine.run(config, new com.tradeevo.core.data.DataSet(files, none), RunMode.BACKTEST, null, null);
        RunRecord withGaps = engine.run(config, new com.tradeevo.core.data.DataSet(files, gaps), RunMode.BACKTEST, null, null);

        assertThat(complete.warnings()).isEmpty();
        assertThat(withGaps.manifest().closedDates()).containsExactly(LocalDate.of(2024, 1, 6));
        assertThat(withGaps.manifest().missingDates()).containsExactly(LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 4));
        assertThat(withGaps.manifest().dataHash()).as("coverage is part of the data hash")
                .isNotEqualTo(complete.manifest().dataHash());
        assertThat(withGaps.warnings()).containsExactly("2 date(s) have no data file and were skipped: 2024-01-03, "
                + "2024-01-04. Mark non-trading days with {\"session\": \"closed\"} files so real gaps stand out.");
    }
}
