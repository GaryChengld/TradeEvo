package com.tradeevo.core.rule.types;

import static com.tradeevo.core.rule.ParamSpec.optional;
import static com.tradeevo.core.rule.ParamSpec.required;

import java.math.BigDecimal;
import java.util.List;


import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.rule.ParamSchema;
import com.tradeevo.core.rule.ParamType;
import com.tradeevo.core.rule.Rule;
import com.tradeevo.core.rule.RuleFactory;
import com.tradeevo.core.rule.RuleType;
import com.tradeevo.core.rule.Truth;
import com.tradeevo.core.rule.Values;

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
        return "Matches when a numeric fact lies between min and max. A missing, non-numeric, NaN or infinite fact is UNKNOWN. Compared as exact decimals.";
    }

    @Override
    public ParamSchema schema() {
        return SCHEMA;
    }

    @Override
    public List<String> validateParams(JsonNode params, String path) {
        if (params.get("min").decimalValue().compareTo(params.get("max").decimalValue()) > 0) {
            return List.of(path + ": 'min' must be <= 'max'");
        }
        return List.of();
    }

    @Override
    public Rule create(JsonNode params, RuleFactory factory) {
        String field = params.get("field").textValue();
        BigDecimal min = params.get("min").decimalValue();
        BigDecimal max = params.get("max").decimalValue();
        boolean inclusive = params.get("inclusive").booleanValue();
        return facts -> {
            BigDecimal value = facts.get(field) instanceof Number number ? Values.toDecimal(number) : null;
            if (value == null) {
                return Truth.UNKNOWN;
            }
            int low = value.compareTo(min);
            int high = value.compareTo(max);
            return Truth.of(inclusive ? low >= 0 && high <= 0 : low > 0 && high < 0);
        };
    }
}
