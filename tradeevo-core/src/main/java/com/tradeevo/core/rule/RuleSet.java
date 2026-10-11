package com.tradeevo.core.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The built rules of one strategy config, in config order. Built once per run: it counts each named
 * rule's outcomes, which the engine reports as the run's rule stats. Not thread-safe.
 *
 * <p>{@link #evaluate} returns the raw three-valued outcome. {@link #matches}, {@link #matching} and
 * {@link #anyMatches} turn UNKNOWN into a decision using each rule's {@link UnknownPolicy}.
 */
public final class RuleSet {

    /**
     * A built rule with its config name, action and unknown policy.
     *
     * @param fields the facts the rule refers to, used to name missing facts in errors
     */
    public record NamedRule(String name, String action, UnknownPolicy onUnknown, Set<String> fields, Rule rule) {

        public NamedRule {
            onUnknown = onUnknown != null ? onUnknown : UnknownPolicy.NO_MATCH;
            fields = fields == null ? Set.of() : Set.copyOf(fields);
        }
    }

    private final List<NamedRule> rules;
    private final Map<String, NamedRule> byName = new LinkedHashMap<>();
    private final Map<String, long[]> counts = new LinkedHashMap<>();

    public RuleSet(List<NamedRule> rules) {
        this.rules = List.copyOf(rules);
        for (NamedRule rule : this.rules) {
            if (byName.put(rule.name(), rule) != null) {
                throw new IllegalArgumentException("Duplicate rule name: " + rule.name());
            }
            counts.put(rule.name(), new long[3]);
        }
    }

    public List<NamedRule> all() {
        return rules;
    }

    public Optional<NamedRule> get(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** The raw outcome of the rule with this name. Throws if the config has no such rule. */
    public Truth evaluate(String name, Facts facts) {
        return evaluate(require(name), facts);
    }

    /**
     * Whether the rule matches, applying its unknown policy.
     *
     * @throws UnknownFactsException if the outcome is UNKNOWN and the policy is FAIL
     */
    public boolean matches(String name, Facts facts) {
        return decide(require(name), facts);
    }

    /** All rules that match (applying each one's unknown policy), in config order. */
    public List<NamedRule> matching(Facts facts) {
        List<NamedRule> matched = new ArrayList<>();
        for (NamedRule rule : rules) {
            if (decide(rule, facts)) {
                matched.add(rule);
            }
        }
        return matched;
    }

    /** Whether any rule with this action matches (applying unknown policies). Stops at the first match. */
    public boolean anyMatches(String action, Facts facts) {
        for (NamedRule rule : rules) {
            if (Objects.equals(rule.action(), action) && decide(rule, facts)) {
                return true;
            }
        }
        return false;
    }

    /** Outcome counts per rule name, in config order, for the evaluations made so far. */
    public Map<String, RuleStats> stats() {
        Map<String, RuleStats> stats = new LinkedHashMap<>();
        counts.forEach((name, c) -> stats.put(name, new RuleStats(c[0], c[1], c[2])));
        return stats;
    }

    private NamedRule require(String name) {
        NamedRule rule = byName.get(name);
        if (rule == null) {
            throw new IllegalArgumentException("No rule named '" + name + "'; rules: " + byName.keySet());
        }
        return rule;
    }

    private Truth evaluate(NamedRule rule, Facts facts) {
        Truth result = rule.rule().evaluate(facts);
        counts.get(rule.name())[result == Truth.TRUE ? 0 : result == Truth.FALSE ? 1 : 2]++;
        return result;
    }

    private boolean decide(NamedRule rule, Facts facts) {
        return switch (evaluate(rule, facts)) {
            case TRUE -> true;
            case FALSE -> false;
            case UNKNOWN -> switch (rule.onUnknown()) {
                case MATCH -> true;
                case NO_MATCH -> false;
                case FAIL -> throw new UnknownFactsException(unknownMessage(rule, facts));
            };
        };
    }

    private static String unknownMessage(NamedRule rule, Facts facts) {
        List<String> missing = rule.fields().stream()
                .filter(field -> facts.get(field) == null)
                .sorted()
                .toList();
        String reason = missing.isEmpty()
                ? "facts " + rule.fields().stream().sorted().toList() + " have incompatible or unparseable values"
                : "missing facts " + missing;
        return "rule '" + rule.name() + "' is UNKNOWN (onUnknown=FAIL): " + reason;
    }
}
