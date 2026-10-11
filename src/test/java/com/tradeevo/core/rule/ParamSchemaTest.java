package com.tradeevo.core.rule;

import static com.tradeevo.TestSupport.json;
import static com.tradeevo.core.rule.ParamSpec.optional;
import static com.tradeevo.core.rule.ParamSpec.required;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ParamSchemaTest {

    private final ParamSchema schema = ParamSchema.of(
            required("field", ParamType.STRING, "f"),
            required("count", ParamType.INTEGER, "c").withMin(1).withMax(10),
            optional("mode", ParamType.STRING, "m").withAllowed("a", "b").withDefault("a"),
            optional("flag", ParamType.BOOLEAN, "b").withDefault(true),
            optional("items", ParamType.ARRAY, "i").withMin(1));

    @Test
    void acceptsValidParams() {
        assertThat(schema.validate(json("{'field': 'x', 'count': 3, 'mode': 'b', 'items': [1]}"), "p")).isEmpty();
    }

    @Test
    void reportsEveryErrorWithItsPath() {
        assertThat(schema.validate(json("{'count': 11, 'mode': 'z', 'flag': 'yes', 'items': [], 'extra': 1}"), "rules[0].params"))
                .containsExactlyInAnyOrder(
                        "rules[0].params.extra: unknown parameter",
                        "rules[0].params.field: required",
                        "rules[0].params.count: must be <= 10",
                        "rules[0].params.mode: must be one of [a, b]",
                        "rules[0].params.flag: must be a boolean",
                        "rules[0].params.items: must have at least 1 item(s)");
    }

    @Test
    void rejectsWrongNumericTypeAndBelowMin() {
        assertThat(schema.validate(json("{'field': 'x', 'count': 1.5}"), "p")).containsExactly("p.count: must be an integer");
        assertThat(schema.validate(json("{'field': 'x', 'count': 0}"), "p")).containsExactly("p.count: must be >= 1");
    }

    @Test
    void nullParamsCountAsEmptyAndNonObjectsAreRejected() {
        assertThat(schema.validate(null, "p")).containsExactlyInAnyOrder("p.field: required", "p.count: required");
        assertThat(schema.validate(json("[1]"), "p")).containsExactly("p: must be an object");
    }

    @Test
    void fillsDefaultsWithoutOverridingGivenValues() {
        assertThat(schema.withDefaults(json("{'field': 'x', 'flag': false}")))
                .isEqualTo(json("{'field': 'x', 'flag': false, 'mode': 'a'}"));
    }
}
