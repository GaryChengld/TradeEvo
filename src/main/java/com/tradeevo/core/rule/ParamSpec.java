package com.tradeevo.core.rule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Describes one rule parameter. {@code min}/{@code max} bound the value of numbers and the size of
 * arrays; {@code allowed} restricts strings.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParamSpec(
        String name,
        ParamType type,
        boolean required,
        JsonNode defaultValue,
        Double min,
        Double max,
        List<String> allowed,
        String description) {

    public static ParamSpec required(String name, ParamType type, String description) {
        return new ParamSpec(name, type, true, null, null, null, null, description);
    }

    public static ParamSpec optional(String name, ParamType type, String description) {
        return new ParamSpec(name, type, false, null, null, null, null, description);
    }

    public ParamSpec withDefault(boolean value) {
        return withDefault(JsonNodeFactory.instance.booleanNode(value));
    }

    public ParamSpec withDefault(double value) {
        return withDefault(JsonNodeFactory.instance.numberNode(value));
    }

    public ParamSpec withDefault(String value) {
        return withDefault(JsonNodeFactory.instance.textNode(value));
    }

    private ParamSpec withDefault(JsonNode value) {
        return new ParamSpec(name, type, false, value, min, max, allowed, description);
    }

    public ParamSpec withMin(double value) {
        return new ParamSpec(name, type, required, defaultValue, value, max, allowed, description);
    }

    public ParamSpec withMax(double value) {
        return new ParamSpec(name, type, required, defaultValue, min, value, allowed, description);
    }

    public ParamSpec withAllowed(String... values) {
        return new ParamSpec(name, type, required, defaultValue, min, max, List.of(values), description);
    }
}
