package com.tradeevo.core.rule.types;

import static com.tradeevo.core.rule.ParamSpec.optional;
import static com.tradeevo.core.rule.ParamSpec.required;

import java.util.List;


import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.rule.ParamSchema;
import com.tradeevo.core.rule.ParamType;
import com.tradeevo.core.rule.Rule;
import com.tradeevo.core.rule.RuleFactory;
import com.tradeevo.core.rule.RuleType;
import com.tradeevo.core.rule.Truth;

/** {@code min <= field <= max} (or strict, when not inclusive). */
public class BetweenRule implements RuleType {

    private static final ParamSchema SCHEMA = ParamSchema.of(
            required("field", ParamType.STRING, "Numeric fact to test"),
            required("min", ParamType.NUMBER, "Lower bound"),
            required("max", ParamType.NUMBER, "Upper bound"),
            optional("inclusive", ParamType.BOOLEAN, "Whether the bounds themselves match").withDefault(true));

    @Override
    public String name() {
        return "Between";
    }

    @Override
    public String description() {
        return "Matches when a numeric fact lies between min and max. A missing or non-numeric fact is UNKNOWN.";
    }

    @Override
    public ParamSchema schema() {
        return SCHEMA;
    }

    @Override
    public List<String> validateParams(JsonNode params, String path) {
        if (params.get("min").doubleValue() > params.get("max").doubleValue()) {
            return List.of(path + ": 'min' must be <= 'max'");
        }
        return List.of();
    }

    @Override
    public Rule create(JsonNode params, RuleFactory factory) {
        String field = params.get("field").textValue();
        double min = params.get("min").doubleValue();
        double max = params.get("max").doubleValue();
        boolean inclusive = params.get("inclusive").booleanValue();
        return facts -> {
            if (!(facts.get(field) instanceof Number number)) {
                return Truth.UNKNOWN;
            }
            double value = number.doubleValue();
            return Truth.of(inclusive ? value >= min && value <= max : value > min && value < max);
        };
    }
}
