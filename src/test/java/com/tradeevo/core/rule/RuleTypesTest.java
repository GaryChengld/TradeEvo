package com.tradeevo.core.rule;

import static com.tradeevo.TestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tradeevo.TestSupport;

class RuleTypesTest {

    private final RuleFactory factory = TestSupport.ruleFactory();

    private Rule rule(String name, String params) {
        return factory.create(name, json(params));
    }

    private static Facts facts(Object... keyValues) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return Facts.of(map);
    }

    @Test
    void compareWithConstant() {
        Rule gt = rule("Compare", "{'field': 'x', 'operator': '>', 'value': 0.3}");
        assertThat(gt.evaluate(facts("x", 0.31))).isTrue();
        assertThat(gt.evaluate(facts("x", 0.3))).isFalse();
        assertThat(gt.evaluate(facts("x", 1))).as("integer vs double").isTrue();

        Rule eq = rule("Compare", "{'field': 'side', 'operator': '==', 'value': 'PUT'}");
        assertThat(eq.evaluate(facts("side", "PUT"))).isTrue();
        assertThat(eq.evaluate(facts("side", "CALL"))).isFalse();
    }

    @Test
    void compareWithOtherField() {
        Rule rule = rule("Compare", "{'field': 'a', 'operator': '<=', 'otherField': 'b'}");
        assertThat(rule.evaluate(facts("a", 1, "b", 2))).isTrue();
        assertThat(rule.evaluate(facts("a", 3, "b", 2))).isFalse();
    }

    @Test
    void compareNeverMatchesMissingOrIncompatibleValues() {
        Rule ne = rule("Compare", "{'field': 'x', 'operator': '!=', 'value': 1}");
        assertThat(ne.evaluate(facts())).isFalse();
        assertThat(ne.evaluate(facts("x", "text"))).isFalse();
        assertThat(ne.evaluate(facts("x", 2))).isTrue();
    }

    @Test
    void between() {
        Rule inclusive = rule("Between", "{'field': 'x', 'min': 1, 'max': 2}");
        assertThat(inclusive.evaluate(facts("x", 1))).isTrue();
        assertThat(inclusive.evaluate(facts("x", 2.0))).isTrue();
        assertThat(inclusive.evaluate(facts("x", 2.01))).isFalse();
        assertThat(inclusive.evaluate(facts("x", "1.5"))).as("non-numeric").isFalse();

        Rule exclusive = rule("Between", "{'field': 'x', 'min': 1, 'max': 2, 'inclusive': false}");
        assertThat(exclusive.evaluate(facts("x", 1))).isFalse();
        assertThat(exclusive.evaluate(facts("x", 1.5))).isTrue();
    }

    @Test
    void in() {
        Rule rule = rule("In", "{'field': 'k', 'values': ['a', 2, true]}");
        assertThat(rule.evaluate(facts("k", "a"))).isTrue();
        assertThat(rule.evaluate(facts("k", 2.0))).isTrue();
        assertThat(rule.evaluate(facts("k", true))).isTrue();
        assertThat(rule.evaluate(facts("k", "b"))).isFalse();
        assertThat(rule.evaluate(facts())).isFalse();
    }

    @Test
    void timeWindowAcceptsTimesDateTimesAndStrings() {
        Rule rule = rule("TimeWindow", "{'field': 't', 'start': '09:30', 'end': '16:00'}");
        assertThat(rule.evaluate(facts("t", "09:30"))).as("start is inclusive").isTrue();
        assertThat(rule.evaluate(facts("t", "2024-01-02T12:00:00"))).isTrue();
        assertThat(rule.evaluate(facts("t", "2024-01-02T12:00:00-05:00"))).isTrue();
        assertThat(rule.evaluate(facts("t", LocalDateTime.of(2024, 1, 2, 16, 0)))).as("end is exclusive").isFalse();
        assertThat(rule.evaluate(facts("t", LocalTime.of(9, 29)))).isFalse();
        assertThat(rule.evaluate(facts("t", "not a time"))).isFalse();
    }

    @Test
    void timeWindowWrapsPastMidnight() {
        Rule rule = rule("TimeWindow", "{'field': 't', 'start': '22:00', 'end': '02:00'}");
        assertThat(rule.evaluate(facts("t", "23:00"))).isTrue();
        assertThat(rule.evaluate(facts("t", "01:59"))).isTrue();
        assertThat(rule.evaluate(facts("t", "12:00"))).isFalse();
    }

    @Test
    void compositesNest() {
        Rule rule = rule("All", """
                {'rules': [
                  {'rule': 'Compare', 'params': {'field': 'x', 'operator': '>', 'value': 0}},
                  {'rule': 'Not', 'params': {'rule':
                    {'rule': 'Any', 'params': {'rules': [
                      {'rule': 'In', 'params': {'field': 'tag', 'values': ['skip']}},
                      {'rule': 'Between', 'params': {'field': 'x', 'min': 10, 'max': 20}}]}}}}]}
                """);
        assertThat(rule.evaluate(facts("x", 5, "tag", "keep"))).isTrue();
        assertThat(rule.evaluate(facts("x", 5, "tag", "skip"))).isFalse();
        assertThat(rule.evaluate(facts("x", 15, "tag", "keep"))).isFalse();
        assertThat(rule.evaluate(facts("x", -1, "tag", "keep"))).isFalse();
    }

    @Test
    void factsFromJsonConvertsValues() {
        Facts facts = Facts.fromJson(json("{'n': 1.5, 's': 'a', 'b': true, 'list': [1], 'obj': {'k': 'v'}}"));
        assertThat(facts.asMap()).containsEntry("n", 1.5).containsEntry("s", "a").containsEntry("b", true)
                .containsEntry("obj", Map.of("k", "v"));
        assertThat(facts.with("extra", 1).has("extra")).isTrue();
        assertThat(facts.has("extra")).as("facts are immutable").isFalse();
    }
}
