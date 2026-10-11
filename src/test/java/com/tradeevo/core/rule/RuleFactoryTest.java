package com.tradeevo.core.rule;

import static com.tradeevo.TestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tradeevo.TestSupport;
import com.tradeevo.core.error.InvalidConfigException;

class RuleFactoryTest {

    private final RuleFactory factory = TestSupport.ruleFactory();

    @Test
    void reportsErrorsInNestedRulesWithFullPaths() {
        List<RuleConfig> rules = List.of(
                new RuleConfig("entry", "Compare", json("{'field': 'x', 'operator': '>', 'value': 1}"), "OPEN"),
                new RuleConfig("exit", "Any", json("""
                        {'rules': [
                          {'rule': 'Compare', 'params': {'operator': '~', 'value': 1}},
                          {'rule': 'Nope', 'params': {}},
                          {'rule': 'Between', 'params': {'field': 'x', 'min': 2, 'max': 1}, 'extra': 1}]}
                        """), "CLOSE"));

        assertThat(factory.validateRuleConfigs(rules)).containsExactlyInAnyOrder(
                "rules[1].params.rules[0].params.field: required",
                "rules[1].params.rules[0].params.operator: must be one of [>, >=, <, <=, ==, !=]",
                "rules[1].params.rules[1].rule: unknown rule type 'Nope'",
                "rules[1].params.rules[2].extra: unknown field (nested rules have only 'rule' and 'params')",
                "rules[1].params.rules[2].params: 'min' must be <= 'max'");
    }

    @Test
    void reportsMissingAndDuplicateNamesAndBadRuleTypes() {
        List<RuleConfig> rules = Arrays.asList(
                new RuleConfig("a", "Compare", json("{'field': 'x', 'operator': '>', 'value': 1, 'otherField': 'y'}"), null),
                new RuleConfig("a", null, null, null),
                new RuleConfig(null, "Not", json("{'rule': 'x'}"), null),
                null);

        assertThat(factory.validateRuleConfigs(rules)).containsExactlyInAnyOrder(
                "rules[0].params: exactly one of 'value' or 'otherField' is required",
                "rules[1].name: duplicate rule name 'a'",
                "rules[1].rule: required",
                "rules[2].name: required",
                "rules[2].params.rule: must be a rule object",
                "rules[3]: must be an object");
    }

    @Test
    void buildsRuleSetInConfigOrder() {
        RuleSet set = factory.buildRuleSet(List.of(
                new RuleConfig("high", "Compare", json("{'field': 'x', 'operator': '>', 'value': 5}"), "SELL"),
                new RuleConfig("positive", "Compare", json("{'field': 'x', 'operator': '>', 'value': 0}"), "BUY"),
                new RuleConfig("negative", "Compare", json("{'field': 'x', 'operator': '<', 'value': 0}"), "SELL")));
        Facts facts = Facts.of(Map.of("x", 10));

        assertThat(set.matching(facts)).extracting(RuleSet.NamedRule::name).containsExactly("high", "positive");
        assertThat(set.matches("negative", facts)).isFalse();
        assertThat(set.anyMatches("SELL", facts)).isTrue();
        assertThat(set.anyMatches("HOLD", facts)).isFalse();
        assertThat(set.get("positive")).map(RuleSet.NamedRule::action).hasValue("BUY");
        assertThatThrownBy(() -> set.matches("missing", facts)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildingAnInvalidRuleSetThrowsWithAllErrors() {
        assertThatThrownBy(() -> factory.create("Compare", json("{}")))
                .isInstanceOfSatisfying(InvalidConfigException.class,
                        e -> assertThat(e.getErrors()).containsExactlyInAnyOrder(
                                "Compare.params.field: required", "Compare.params.operator: required"));
        assertThatThrownBy(() -> factory.buildRuleSet(List.of(new RuleConfig("r", "Compare", json("{}"), null))))
                .isInstanceOfSatisfying(InvalidConfigException.class, e -> assertThat(e.getErrors()).containsExactlyInAnyOrder(
                                "rules[0].params.field: required", "rules[0].params.operator: required"));
    }
}
