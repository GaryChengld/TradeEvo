package com.tradeevo.core.rule.types;

import static com.tradeevo.core.rule.ParamSpec.required;


import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.rule.ParamSchema;
import com.tradeevo.core.rule.ParamType;
import com.tradeevo.core.rule.Rule;
import com.tradeevo.core.rule.RuleFactory;
import com.tradeevo.core.rule.RuleType;

/** Logical NOT of a nested rule. */
public class NotRule implements RuleType {

    private static final ParamSchema SCHEMA = ParamSchema.of(
            required("rule", ParamType.RULE, "Nested rule to negate"));

    @Override
    public String name() {
        return "Not";
    }

    @Override
    public String description() {
        return "Negates the nested rule. UNKNOWN stays UNKNOWN.";
    }

    @Override
    public ParamSchema schema() {
        return SCHEMA;
    }

    @Override
    public Rule create(JsonNode params, RuleFactory factory) {
        Rule inner = factory.createNode(params.get("rule"));
        return facts -> inner.evaluate(facts).not();
    }
}
