package com.tradeevo.core.rule.types;

import static com.tradeevo.core.rule.ParamSpec.required;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.rule.ParamSchema;
import com.tradeevo.core.rule.ParamType;
import com.tradeevo.core.rule.Rule;
import com.tradeevo.core.rule.RuleFactory;
import com.tradeevo.core.rule.RuleType;

/** Logical OR of nested rules. */
@Component
public class AnyRule implements RuleType {

    private static final ParamSchema SCHEMA = ParamSchema.of(
            required("rules", ParamType.RULE_LIST, "Nested rules, at least one of which must match").withMin(1));

    @Override
    public String name() {
        return "Any";
    }

    @Override
    public String description() {
        return "Matches when at least one nested rule matches.";
    }

    @Override
    public ParamSchema schema() {
        return SCHEMA;
    }

    @Override
    public Rule create(JsonNode params, RuleFactory factory) {
        List<Rule> rules = new ArrayList<>();
        params.get("rules").forEach(node -> rules.add(factory.createNode(node)));
        return facts -> rules.stream().anyMatch(rule -> rule.evaluate(facts));
    }
}
