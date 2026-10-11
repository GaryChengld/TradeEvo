package com.tradeevo.core.rule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The built rules of one strategy config, in config order. */
public final class RuleSet {

    /** A built rule together with its config name and action. */
    public record NamedRule(String name, String action, Rule rule) {

        public boolean evaluate(Facts facts) {
            return rule.evaluate(facts);
        }
    }

    private final List<NamedRule> rules;
    private final Map<String, NamedRule> byName = new LinkedHashMap<>();

    public RuleSet(List<NamedRule> rules) {
        this.rules = List.copyOf(rules);
        for (NamedRule rule : this.rules) {
            if (byName.put(rule.name(), rule) != null) {
                throw new IllegalArgumentException("Duplicate rule name: " + rule.name());
            }
        }
    }

    public List<NamedRule> all() {
        return rules;
    }

    public Optional<NamedRule> get(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** Evaluates the rule with this name. Throws if the config has no such rule. */
    public boolean matches(String name, Facts facts) {
        NamedRule rule = byName.get(name);
        if (rule == null) {
            throw new IllegalArgumentException("No rule named '" + name + "'; rules: " + byName.keySet());
        }
        return rule.evaluate(facts);
    }

    /** All rules that match, in config order. */
    public List<NamedRule> matching(Facts facts) {
        return rules.stream().filter(rule -> rule.evaluate(facts)).toList();
    }

    /** Whether any rule with this action matches. */
    public boolean anyMatches(String action, Facts facts) {
        return rules.stream()
                .filter(rule -> Objects.equals(rule.action(), action))
                .anyMatch(rule -> rule.evaluate(facts));
    }
}
