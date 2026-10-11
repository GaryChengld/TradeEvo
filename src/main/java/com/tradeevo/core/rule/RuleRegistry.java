package com.tradeevo.core.rule;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

/** All available {@link RuleType}s, by name. */
@Component
public class RuleRegistry {

    private final Map<String, RuleType> types = new TreeMap<>();

    public RuleRegistry(List<RuleType> ruleTypes) {
        for (RuleType type : ruleTypes) {
            RuleType previous = types.put(type.name(), type);
            if (previous != null) {
                throw new IllegalStateException("Duplicate rule type name '" + type.name() + "': "
                        + previous.getClass().getName() + " and " + type.getClass().getName());
            }
        }
    }

    public Optional<RuleType> find(String name) {
        return Optional.ofNullable(name).map(types::get);
    }

    public Collection<RuleType> all() {
        return types.values();
    }
}
