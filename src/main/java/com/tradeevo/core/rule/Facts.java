package com.tradeevo.core.rule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The named values a rule is evaluated against. A strategy builds facts from its own data
 * (e.g. one record plus derived values such as {@code pnl}); the core never interprets them.
 * Immutable.
 */
public final class Facts {

    private final Map<String, Object> values;

    private Facts(Map<String, Object> values) {
        this.values = values;
    }

    public static Facts of(Map<String, ?> values) {
        return new Facts(new LinkedHashMap<>(values));
    }

    /** Builds facts from the fields of a JSON object; nested values become Lists and Maps. */
    public static Facts fromJson(JsonNode object) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (object != null && object.isObject()) {
            object.properties().forEach(entry -> map.put(entry.getKey(), Values.fromJson(entry.getValue())));
        }
        return new Facts(map);
    }

    /** Returns a copy with one value added or replaced. */
    public Facts with(String name, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        copy.put(name, value);
        return new Facts(copy);
    }

    /** The value of a fact, or null when absent. */
    public Object get(String name) {
        return values.get(name);
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
