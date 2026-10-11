package com.tradeevo.strategies.example;

import static com.tradeevo.TestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tradeevo.TestSupport;
import com.tradeevo.core.data.DataFile;
import com.tradeevo.core.data.DataRepository;
import com.tradeevo.core.engine.RunRecord;
import com.tradeevo.core.engine.RunStatus;
import com.tradeevo.core.engine.StrategyEngine;
import com.tradeevo.core.rule.RuleConfig;
import com.tradeevo.core.strategy.RunMode;
import com.tradeevo.core.strategy.StrategyClassRegistry;
import com.tradeevo.core.strategy.StrategyConfig;

class ThresholdSignalStrategyTest {

    private final StrategyEngine engine = new StrategyEngine(
            new StrategyClassRegistry(List.of(new ThresholdSignalStrategy())), TestSupport.ruleFactory(), Clock.systemUTC());

    private List<DataFile> exampleData() {
        return new DataRepository(TestSupport.EXAMPLE_DATA_DIR, TestSupport.MAPPER)
                .load("example", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));
    }

    @Test
    void runsTheExampleConfigOverTheExampleData() {
        RunRecord record = engine.run(TestSupport.exampleConfig(), exampleData(), RunMode.BACKTEST, null, null);

        assertThat(record.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(record.metrics()).containsEntry("records", 9).containsEntry("signals", 3);
        assertThat(record.metrics().get("totalChange").doubleValue()).isCloseTo(2.0, within(1e-9));
        assertThat(record.metrics().get("winRate").doubleValue()).isCloseTo(2.0 / 3, within(1e-9));
        assertThat(record.details().get("signals")).hasSize(6);
        assertThat(record.details().get("signals").get(5).get("reason").asText()).isEqualTo("END");
    }

    @Test
    void identicalRunsGiveIdenticalResults() {
        RunRecord first = engine.run(TestSupport.exampleConfig(), exampleData(), RunMode.BACKTEST, null, null);
        RunRecord second = engine.run(TestSupport.exampleConfig(), exampleData(), RunMode.BACKTEST, null, null);

        assertThat(second.metrics()).isEqualTo(first.metrics());
        assertThat(second.details()).isEqualTo(first.details());
    }

    @Test
    void closeAtEndCanBeTurnedOff() {
        StrategyConfig base = TestSupport.exampleConfig();
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
        RunRecord record = engine.run(TestSupport.exampleConfig(),
                List.of(new DataFile(null, json("{'rows': []}"))), RunMode.EVALUATION, null, null);

        assertThat(record.status()).isEqualTo(RunStatus.FAILED);
        assertThat(record.error()).contains("request data: expected an object with a 'records' array");
    }
}
