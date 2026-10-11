package com.tradeevo.core.rule;

import static com.tradeevo.TestSupport.json;
import static com.tradeevo.core.rule.Truth.FALSE;
import static com.tradeevo.core.rule.Truth.TRUE;
import static com.tradeevo.core.rule.Truth.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tradeevo.TestSupport;

class RuleTypesTest {

    private final RuleFactory factory = TestSupport.ruleFactory();

    private Rule rule(String name, String params) {
        return factory.create(name, json(params));
    }

    static Facts facts(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return Facts.of(map);
    }

    @Test
    void compareWithConstant() {
        Rule gt = rule("Compare", "{'field': 'x', 'operator': '>', 'value': 0.3}");
        assertThat(gt.evaluate(facts("x", 0.31))).isEqualTo(TRUE);
        assertThat(gt.evaluate(facts("x", 0.3))).isEqualTo(FALSE);
        assertThat(gt.evaluate(facts("x", 1))).as("integer vs double").isEqualTo(TRUE);

        Rule eq = rule("Compare", "{'field': 'side', 'operator': '==', 'value': 'PUT'}");
        assertThat(eq.evaluate(facts("side", "PUT"))).isEqualTo(TRUE);
        assertThat(eq.evaluate(facts("side", "CALL"))).isEqualTo(FALSE);
    }

    @Test
    void compareWithOtherField() {
        Rule rule = rule("Compare", "{'field': 'a', 'operator': '<=', 'otherField': 'b'}");
        assertThat(rule.evaluate(facts("a", 1, "b", 2))).isEqualTo(TRUE);
        assertThat(rule.evaluate(facts("a", 3, "b", 2))).isEqualTo(FALSE);
        assertThat(rule.evaluate(facts("a", 3))).isEqualTo(UNKNOWN);
    }

    @Test
    void compareIsUnknownForMissingNullOrIncompatibleValues() {
        Rule ne = rule("Compare", "{'field': 'x', 'operator': '!=', 'value': 1}");
        assertThat(ne.evaluate(facts())).isEqualTo(UNKNOWN);
        assertThat(ne.evaluate(facts("x", null))).isEqualTo(UNKNOWN);
        assertThat(ne.evaluate(facts("x", "text"))).isEqualTo(UNKNOWN);
        assertThat(ne.evaluate(facts("x", 2))).isEqualTo(TRUE);
    }

    @Test
    void between() {
        Rule inclusive = rule("Between", "{'field': 'x', 'min': 1, 'max': 2}");
        assertThat(inclusive.evaluate(facts("x", 1))).isEqualTo(TRUE);
        assertThat(inclusive.evaluate(facts("x", 2.0))).isEqualTo(TRUE);
        assertThat(inclusive.evaluate(facts("x", 2.01))).isEqualTo(FALSE);
        assertThat(inclusive.evaluate(facts("x", "1.5"))).as("non-numeric").isEqualTo(UNKNOWN);
        assertThat(inclusive.evaluate(facts())).isEqualTo(UNKNOWN);

        Rule exclusive = rule("Between", "{'field': 'x', 'min': 1, 'max': 2, 'inclusive': false}");
        assertThat(exclusive.evaluate(facts("x", 1))).isEqualTo(FALSE);
        assertThat(exclusive.evaluate(facts("x", 1.5))).isEqualTo(TRUE);
    }

    @Test
    void in() {
        Rule rule = rule("In", "{'field': 'k', 'values': ['a', 2, true]}");
        assertThat(rule.evaluate(facts("k", "a"))).isEqualTo(TRUE);
        assertThat(rule.evaluate(facts("k", 2.0))).isEqualTo(TRUE);
        assertThat(rule.evaluate(facts("k", true))).isEqualTo(TRUE);
        assertThat(rule.evaluate(facts("k", "b"))).isEqualTo(FALSE);
        assertThat(rule.evaluate(facts())).isEqualTo(UNKNOWN);
    }

    @Test
    void timeWindowAcceptsTimesDateTimesAndStrings() {
        Rule rule = rule("TimeWindow", "{'field': 't', 'start': '09:30', 'end': '16:00'}");
        assertThat(rule.evaluate(facts("t", "09:30"))).as("start is inclusive").isEqualTo(TRUE);
        assertThat(rule.evaluate(facts("t", "2024-01-02T12:00:00"))).isEqualTo(TRUE);
        assertThat(rule.evaluate(facts("t", "2024-01-02T12:00:00-05:00"))).isEqualTo(TRUE);
        assertThat(rule.evaluate(facts("t", LocalDateTime.of(2024, 1, 2, 16, 0)))).as("end is exclusive").isEqualTo(FALSE);
        assertThat(rule.evaluate(facts("t", LocalTime.of(9, 29)))).isEqualTo(FALSE);
        assertThat(rule.evaluate(facts("t", "not a time"))).isEqualTo(UNKNOWN);
    }

    @Test
    void timeWindowWrapsPastMidnight() {
        Rule rule = rule("TimeWindow", "{'field': 't', 'start': '22:00', 'end': '02:00'}");
        assertThat(rule.evaluate(facts("t", "23:00"))).isEqualTo(TRUE);
        assertThat(rule.evaluate(facts("t", "01:59"))).isEqualTo(TRUE);
        assertThat(rule.evaluate(facts("t", "12:00"))).isEqualTo(FALSE);
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
        assertThat(rule.evaluate(facts("x", 5, "tag", "keep"))).isEqualTo(TRUE);
        assertThat(rule.evaluate(facts("x", 5, "tag", "skip"))).isEqualTo(FALSE);
        assertThat(rule.evaluate(facts("x", 15, "tag", "keep"))).isEqualTo(FALSE);
        assertThat(rule.evaluate(facts("x", -1, "tag", "keep"))).isEqualTo(FALSE);
        assertThat(rule.evaluate(facts("x", 5))).as("tag missing, nothing else decides").isEqualTo(UNKNOWN);
        assertThat(rule.evaluate(facts("x", -1))).as("FALSE dominates UNKNOWN in All").isEqualTo(FALSE);
    }

    @Test
    void notOfAnUnknownConditionStaysUnknown() {
        Rule notHigh = rule("Not", "{'rule': {'rule': 'Compare', 'params': {'field': 'rsi', 'operator': '>', 'value': 55}}}");
        assertThat(notHigh.evaluate(facts("rsi", 40))).isEqualTo(TRUE);
        assertThat(notHigh.evaluate(facts("rsi", 60))).isEqualTo(FALSE);
        assertThat(notHigh.evaluate(facts())).isEqualTo(UNKNOWN);
    }

    @Test
    void anyIsTrueIfOneIsTrueEvenWhenOthersAreUnknown() {
        Rule rule = rule("Any", """
                {'rules': [
                  {'rule': 'Compare', 'params': {'field': 'a', 'operator': '>', 'value': 0}},
                  {'rule': 'Compare', 'params': {'field': 'b', 'operator': '>', 'value': 0}}]}
                """);
        assertThat(rule.evaluate(facts("b", 1))).isEqualTo(TRUE);
        assertThat(rule.evaluate(facts("b", -1))).isEqualTo(UNKNOWN);
        assertThat(rule.evaluate(facts("a", -1, "b", -1))).isEqualTo(FALSE);
    }

    @Test
    void factsFromJsonConvertsValues() {
        Facts facts = Facts.fromJson(json("{'n': 1.5, 's': 'a', 'b': true, 'list': [1], 'obj': {'k': 'v'}}"));
        assertThat(facts.asMap()).containsEntry("n", 1.5).containsEntry("s", "a").containsEntry("b", true)
                .containsEntry("obj", Map.of("k", "v"));
        assertThat(facts.with("extra", 1).has("extra")).isTrue();
        assertThat(facts.has("extra")).as("facts are immutable").isFalse();
    }

    @Test
    void nonFiniteNumbersAreUnknown() {
        Rule gt = rule("Compare", "{'field': 'x', 'operator': '>', 'value': 100}");
        Rule ne = rule("Compare", "{'field': 'x', 'operator': '!=', 'value': 100}");
        Rule between = rule("Between", "{'field': 'x', 'min': 0, 'max': 1}");

        for (Object value : new Object[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Float.NaN}) {
            assertThat(gt.evaluate(facts("x", value))).as("> with %s", value).isEqualTo(UNKNOWN);
            assertThat(ne.evaluate(facts("x", value))).as("!= with %s", value).isEqualTo(UNKNOWN);
            assertThat(between.evaluate(facts("x", value))).as("Between with %s", value).isEqualTo(UNKNOWN);
        }
        assertThat(rule("In", "{'field': 'x', 'values': [1]}").evaluate(facts("x", Double.NaN))).isEqualTo(FALSE);
    }

    @Test
    void numbersCompareExactly() {
        long big = 9_007_199_254_740_993L; // 2^53 + 1: not representable as a double
        Rule eq = rule("Compare", "{'field': 'a', 'operator': '==', 'otherField': 'b'}");
        assertThat(eq.evaluate(facts("a", big, "b", big - 1))).isEqualTo(FALSE);
        assertThat(eq.evaluate(facts("a", new java.math.BigDecimal("0.30000000000000000001"),
                "b", new java.math.BigDecimal("0.3")))).isEqualTo(FALSE);
        assertThat(eq.evaluate(facts("a", 0.1, "b", new java.math.BigDecimal("0.10")))).isEqualTo(TRUE);
        assertThat(eq.evaluate(facts("a", 2, "b", 2.0f))).isEqualTo(TRUE);
        assertThat(eq.evaluate(facts("a", java.math.BigInteger.TEN.pow(30), "b", new java.math.BigDecimal("1E+30"))))
                .isEqualTo(TRUE);

        Rule between = rule("Between", "{'field': 'x', 'min': 0, 'max': 9007199254740992}");
        assertThat(between.evaluate(facts("x", big))).as("just above max").isEqualTo(FALSE);
        assertThat(between.evaluate(facts("x", big - 1))).isEqualTo(TRUE);
    }
}
