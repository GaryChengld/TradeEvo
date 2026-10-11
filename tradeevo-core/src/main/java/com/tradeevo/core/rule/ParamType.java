package com.tradeevo.core.rule;

import com.fasterxml.jackson.databind.JsonNode;

/** JSON types a rule parameter can take. */
public enum ParamType {
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    /** Any JSON array. */
    ARRAY,
    /** Any scalar: string, number or boolean. */
    VALUE,
    /** A nested rule: {@code {"rule": "...", "params": {...}}}. */
    RULE,
    /** An array of nested rules. */
    RULE_LIST;

    boolean accepts(JsonNode node) {
        return switch (this) {
            case STRING -> node.isTextual();
            case NUMBER -> node.isNumber();
            case INTEGER -> node.isIntegralNumber();
            case BOOLEAN -> node.isBoolean();
            case ARRAY -> node.isArray();
            case VALUE -> node.isTextual() || node.isNumber() || node.isBoolean();
            case RULE -> node.isObject();
            case RULE_LIST -> node.isArray();
        };
    }

    String description() {
        return switch (this) {
            case STRING -> "a string";
            case NUMBER -> "a number";
            case INTEGER -> "an integer";
            case BOOLEAN -> "a boolean";
            case ARRAY -> "an array";
            case VALUE -> "a string, number or boolean";
            case RULE -> "a rule object";
            case RULE_LIST -> "an array of rule objects";
        };
    }
}
