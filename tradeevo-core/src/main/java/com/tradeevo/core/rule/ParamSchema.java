package com.tradeevo.core.rule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** The parameters a rule type accepts. */
public record ParamSchema(List<ParamSpec> params) {

    public ParamSchema {
        params = List.copyOf(params);
    }

    public static ParamSchema of(ParamSpec... specs) {
        return new ParamSchema(List.of(specs));
    }

    public Optional<ParamSpec> spec(String name) {
        return params.stream().filter(p -> p.name().equals(name)).findFirst();
    }

    /**
     * Checks the params object against this schema. Every error is returned, prefixed with
     * {@code path} (e.g. {@code rules[0].params.field: required}). Null params count as {@code {}}.
     */
    public List<String> validate(JsonNode params, String path) {
        List<String> errors = new ArrayList<>();
        if (params != null && !params.isNull() && !params.isObject()) {
            errors.add(path + ": must be an object");
            return errors;
        }
        JsonNode object = params == null || params.isNull() ? JsonNodeFactory.instance.objectNode() : params;

        for (Iterator<String> names = object.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (spec(name).isEmpty()) {
                errors.add(path + "." + name + ": unknown parameter");
            }
        }
        for (ParamSpec spec : this.params) {
            JsonNode value = object.get(spec.name());
            String valuePath = path + "." + spec.name();
            if (value == null || value.isNull()) {
                if (spec.required()) {
                    errors.add(valuePath + ": required");
                }
                continue;
            }
            if (!spec.type().accepts(value)) {
                errors.add(valuePath + ": must be " + spec.type().description());
                continue;
            }
            checkBounds(spec, value, valuePath, errors);
        }
        return errors;
    }

    private static void checkBounds(ParamSpec spec, JsonNode value, String path, List<String> errors) {
        boolean isArray = value.isArray();
        if (value.isNumber() || isArray) {
            double measured = isArray ? value.size() : value.doubleValue();
            String what = isArray ? "must have at least " : "must be >= ";
            if (spec.min() != null && measured < spec.min()) {
                errors.add(path + ": " + what + format(spec.min()) + (isArray ? " item(s)" : ""));
            }
            what = isArray ? "must have at most " : "must be <= ";
            if (spec.max() != null && measured > spec.max()) {
                errors.add(path + ": " + what + format(spec.max()) + (isArray ? " item(s)" : ""));
            }
        }
        if (value.isTextual() && spec.allowed() != null && !spec.allowed().contains(value.textValue())) {
            errors.add(path + ": must be one of " + spec.allowed());
        }
    }

    private static String format(double number) {
        return number == Math.rint(number) ? String.valueOf((long) number) : String.valueOf(number);
    }

    /** Returns a copy of the params with defaults filled in for absent optional parameters. */
    public ObjectNode withDefaults(JsonNode params) {
        ObjectNode result = params != null && params.isObject()
                ? ((ObjectNode) params).deepCopy()
                : JsonNodeFactory.instance.objectNode();
        for (ParamSpec spec : this.params) {
            JsonNode current = result.get(spec.name());
            if ((current == null || current.isNull()) && spec.defaultValue() != null) {
                result.set(spec.name(), spec.defaultValue());
            }
        }
        return result;
    }
}
