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
import com.tradeevo.core.rule.Values;

/** {@code field} equals one of {@code values}. */
@Component
public class InRule implements RuleType {

    private static final ParamSchema SCHEMA = ParamSchema.of(
            required("field", ParamType.STRING, "Fact to test"),
            required("values", ParamType.ARRAY, "Allowed values (strings, numbers or booleans)").withMin(1));

    @Override
    public String name() {
        return "In";
    }

    @Override
    public String description() {
        return "Matches when a fact equals one of the listed values.";
    }

    @Override
    public ParamSchema schema() {
        return SCHEMA;
    }

    @Override
    public List<String> validateParams(JsonNode params, String path) {
        List<String> errors = new ArrayList<>();
        JsonNode values = params.get("values");
        for (int i = 0; i < values.size(); i++) {
            JsonNode value = values.get(i);
            if (!(value.isTextual() || value.isNumber() || value.isBoolean())) {
                errors.add(path + ".values[" + i + "]: must be a string, number or boolean");
            }
        }
        return errors;
    }

    @Override
    public Rule create(JsonNode params, RuleFactory factory) {
        String field = params.get("field").textValue();
        List<Object> values = new ArrayList<>();
        params.get("values").forEach(value -> values.add(Values.fromJson(value)));
        return facts -> {
            Object actual = facts.get(field);
            return actual != null && values.stream().anyMatch(value -> Values.looseEquals(actual, value));
        };
    }
}
