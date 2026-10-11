package com.tradeevo.core.strategy;

import static com.tradeevo.TestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradeevo.core.engine.RunRecord;
import com.tradeevo.core.engine.RunStatus;
import com.tradeevo.core.rule.RuleConfig;

/** JSON nodes are mutable; records must not change when the caller's node or a returned node changes. */
class ImmutabilityTest {

    @Test
    void strategyConfigSettingsAndRuleParams() {
        ObjectNode settings = (ObjectNode) json("{'k': 1}");
        ObjectNode params = (ObjectNode) json("{'field': 'x'}");
        StrategyConfig config = new StrategyConfig("s", 1, null, "c", null, settings,
                List.of(new RuleConfig("r", "Compare", params, null)));

        settings.put("k", 2);
        params.put("field", "y");
        ((ObjectNode) config.settings()).put("k", 3);
        ((ObjectNode) config.settingsOrEmpty()).put("k", 4);
        ((ObjectNode) config.rules().getFirst().params()).put("field", "z");

        assertThat(config.settings()).isEqualTo(json("{'k': 1}"));
        assertThat(config.rules().getFirst().params()).isEqualTo(json("{'field': 'x'}"));
    }

    @Test
    void strategyResultAndRunRecordDetails() {
        ObjectNode details = (ObjectNode) json("{'n': 1}");
        ObjectNode assumptions = (ObjectNode) json("{'costs': 'none'}");
        StrategyResult result = new StrategyResult(Map.of("m", 1), details, List.of(), assumptions);
        RunRecord record = new RunRecord("r", RunStatus.COMPLETED, RunMode.BACKTEST, null, null, null, null,
                Map.of(), details, null, null, null, null, null, null);

        details.put("n", 2);
        assumptions.put("costs", "some");
        ((ObjectNode) result.details()).put("n", 3);
        ((ObjectNode) result.assumptions()).put("costs", "more");
        ((ObjectNode) record.details()).put("n", 4);

        assertThat(result.details()).isEqualTo(json("{'n': 1}"));
        assertThat(result.assumptions()).isEqualTo(json("{'costs': 'none'}"));
        assertThat(record.details()).isEqualTo(json("{'n': 1}"));
    }
}
