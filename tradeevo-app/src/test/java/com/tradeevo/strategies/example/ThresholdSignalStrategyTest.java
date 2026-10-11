package com.tradeevo.strategies.example;

import static com.tradeevo.AppTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tradeevo.AppTestSupport;
import com.tradeevo.core.data.DataFile;
import com.tradeevo.core.data.DataRepository;
import com.tradeevo.core.engine.RunRecord;
import com.tradeevo.core.engine.RunStatus;
import com.tradeevo.core.engine.StrategyEngine;
import com.tradeevo.core.rule.RuleConfig;
import com.tradeevo.core.strategy.RunMode;
import com.tradeevo.core.strategy.StrategyConfig;

class ThresholdSignalStrategyTest {

    private final StrategyEngine engine = StrategyEngine.create(List.of(new ThresholdSignalStrategy()));

    private List<DataFile> exampleData() {
        return new DataRepository(AppTestSupport.EXAMPLE_DATA_DIR, AppTestSupport.MAPPER)
                .load("example", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)).files();
    }

    private RunRecord evaluate(String data) {
        return engine.run(AppTestSupport.exampleConfig(), List.of(new DataFile(null, json(data))),
                RunMode.EVALUATION, null, null);
    }

    @Test
    void runsTheExampleConfigOverTheExampleData() {
        RunRecord record = engine.run(AppTestSupport.exampleConfig(), exampleData(), RunMode.BACKTEST, null, null);

        assertThat(record.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(record.metrics()).containsEntry("records", 9).containsEntry("signals", 3);
        assertThat(record.metrics().get("valueChangeTotal").doubleValue()).isCloseTo(2.0, within(1e-9));
        assertThat(record.metrics().get("positiveSignalRate").doubleValue()).isCloseTo(2.0 / 3, within(1e-9));
        assertThat(record.details().get("signals")).hasSize(6);
        assertThat(record.details().get("signals").get(5).get("reason").asText()).isEqualTo("END");
        assertThat(record.manifest().assumptions().get("model").asText()).isEqualTo("signal-only");
        assertThat(record.ruleStats()).containsKeys("entry", "exit");
        assertThat(record.warnings()).isEmpty();
    }

    @Test
    void identicalRunsGiveIdenticalResults() {
        RunRecord first = engine.run(AppTestSupport.exampleConfig(), exampleData(), RunMode.BACKTEST, null, null);
        RunRecord second = engine.run(AppTestSupport.exampleConfig(), exampleData(), RunMode.BACKTEST, null, null);

        assertThat(second.metrics()).isEqualTo(first.metrics());
        assertThat(second.details()).isEqualTo(first.details());
        assertThat(second.manifest()).isEqualTo(first.manifest());
    }

    @Test
    void closeAtEndCanBeTurnedOff() {
        StrategyConfig base = AppTestSupport.exampleConfig();
        StrategyConfig config = new StrategyConfig(base.id(), base.version(), null, base.strategyClass(), base.dataset(),
                json("{'closeAtEnd': false}"), base.rules());

        RunRecord record = engine.run(config, exampleData(), RunMode.BACKTEST, null, null);

        assertThat(record.metrics()).containsEntry("signals", 2);
        assertThat(record.details().get("openAtEnd").asBoolean()).isTrue();
    }

    @Test
    void requiresOpenAndCloseRules() {
        StrategyConfig config = new StrategyConfig("t", null, null, ThresholdSignalStrategy.class.getName(), null,
                json("{'closeAtEnd': 'yes'}"),
                List.of(new RuleConfig("r", "Compare", json("{'field': 'x', 'operator': '>', 'value': 1}"), "BUY")));

        assertThat(engine.validate(config)).containsExactly(
                "rules[0].action: must be OPEN or CLOSE",
                "rules: at least one rule with action OPEN is required",
                "rules: at least one rule with action CLOSE is required",
                "settings.closeAtEnd: must be a boolean");
    }

    @Test
    void dataInTheWrongFormatFailsTheRun() {
        RunRecord record = evaluate("{'rows': []}");

        assertThat(record.status()).isEqualTo(RunStatus.FAILED);
        assertThat(record.error()).contains("request data: expected an object with a 'records' array");
    }

    @Test
    void outOfOrderOrDuplicateRecordsFailTheRun() {
        RunRecord unsorted = evaluate("""
                {'records': [
                  {'time': '2024-01-03T09:30:00', 'value': 1, 'x': 0.1},
                  {'time': '2024-01-03T09:00:00', 'value': 2, 'x': 0.1}]}
                """);
        RunRecord duplicate = evaluate("""
                {'records': [
                  {'time': '2024-01-03T09:30:00', 'value': 1, 'x': 0.1},
                  {'time': '2024-01-03T09:30:00', 'value': 2, 'x': 0.1}]}
                """);
        RunRecord missingTime = evaluate("{'records': [{'value': 1, 'x': 0.1}]}");

        assertThat(unsorted.status()).isEqualTo(RunStatus.FAILED);
        assertThat(unsorted.error()).isEqualTo("InvalidRecordOrderException: request data records[1]: time "
                + "2024-01-03T09:00:00 is not after request data records[0] (2024-01-03T09:30:00)");
        assertThat(duplicate.error()).contains("duplicate timestamp");
        assertThat(missingTime.error()).contains("request data records[0]: time is missing");
    }

    @Test
    void recordsMissingAFactAreReportedAsUnknown() {
        RunRecord record = evaluate("""
                {'records': [
                  {'time': '2024-01-03T09:30:00', 'value': 1},
                  {'time': '2024-01-03T09:31:00', 'value': 2, 'x': 0.5}]}
                """);

        assertThat(record.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(record.ruleStats().get("entry").unknown()).isEqualTo(1);
        assertThat(record.warnings()).anyMatch(warning -> warning.startsWith("rule 'entry' was UNKNOWN for 1 of"));
    }
}
