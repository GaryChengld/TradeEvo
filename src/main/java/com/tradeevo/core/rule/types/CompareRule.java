package com.tradeevo.core.rule.types;

import static com.tradeevo.core.rule.ParamSpec.optional;
import static com.tradeevo.core.rule.ParamSpec.required;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.rule.ParamSchema;
import com.tradeevo.core.rule.ParamType;
import com.tradeevo.core.rule.Rule;
import com.tradeevo.core.rule.RuleFactory;
import com.tradeevo.core.rule.RuleType;
import com.tradeevo.core.rule.Values;

/** {@code field <operator> value}, or {@code field <operator> otherField}. */
@Component
public class CompareRule implements RuleType {

    private static final ParamSchema SCHEMA = ParamSchema.of(
            required("field", ParamType.STRING, "Fact to test"),
            required("operator", ParamType.STRING, "Comparison operator")
                    .withAllowed(">", ">=", "<", "<=", "==", "!="),
            optional("value", ParamType.VALUE, "Constant to compare against"),
            optional("otherField", ParamType.STRING, "Fact to compare against, instead of 'value'"));

    @Override
    public String name() {
        return "Compare";
    }

    @Override
    public String description() {
        return "Compares a fact with a constant or with another fact. Numbers compare numerically; "
                + "a missing fact or incompatible types never match.";
    }

    @Override
    public ParamSchema schema() {
        return SCHEMA;
    }

    @Override
    public List<String> validateParams(JsonNode params, String path) {
        if (params.has("value") == params.has("otherField")) {
            return List.of(path + ": exactly one of 'value' or 'otherField' is required");
        }
        return List.of();
    }

    @Override
    public Rule create(JsonNode params, RuleFactory factory) {
        String field = params.get("field").textValue();
        String operator = params.get("operator").textValue();
        if (params.has("otherField")) {
            String otherField = params.get("otherField").textValue();
            return facts -> Values.test(facts.get(field), operator, facts.get(otherField));
        }
        Object value = Values.fromJson(params.get("value"));
        return facts -> Values.test(facts.get(field), operator, value);
    }
}
