package com.tradeevo.core.rule.types;

import static com.tradeevo.core.rule.ParamSpec.required;

import java.util.ArrayList;
import java.util.List;


import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.rule.ParamSchema;
import com.tradeevo.core.rule.ParamType;
import com.tradeevo.core.rule.Rule;
import com.tradeevo.core.rule.RuleFactory;
import com.tradeevo.core.rule.RuleType;
import com.tradeevo.core.rule.Truth;

/** Logical AND of nested rules. */
public class AllRule implements RuleType {

    private static final ParamSchema SCHEMA = ParamSchema.of(
            required("rules", ParamType.RULE_LIST, "Nested rules that must all match").withMin(1));

    @Override
    public String name() {
        return "All";
    }

    @Override
    public String description() {
        return "TRUE when every nested rule is TRUE; FALSE when any is FALSE; otherwise UNKNOWN.";
    }

    @Override
    public ParamSchema schema() {
        return SCHEMA;
    }

    @Override
    public Rule create(JsonNode params, RuleFactory factory) {
        List<Rule> rules = new ArrayList<>();
        params.get("rules").forEach(node -> rules.add(factory.createNode(node)));
        return facts -> {
            Truth result = Truth.TRUE;
            for (Rule rule : rules) {
                result = result.and(rule.evaluate(facts));
                if (result == Truth.FALSE) {
                    return result;
                }
            }
            return result;
        };
    }
}
