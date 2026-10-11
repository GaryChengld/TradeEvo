package com.tradeevo.core.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.error.InvalidConfigException;

/** Validates rule configs (including nested rules) and builds them into {@link Rule}s. */
@Component
public class RuleFactory {

    private final RuleRegistry registry;

    public RuleFactory(RuleRegistry registry) {
        this.registry = registry;
    }

    /** Validates the {@code rules} list of a strategy config. */
    public List<String> validateRuleConfigs(List<RuleConfig> rules) {
        List<String> errors = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < rules.size(); i++) {
            String path = "rules[" + i + "]";
            RuleConfig config = rules.get(i);
            if (config == null) {
                errors.add(path + ": must be an object");
                continue;
            }
            if (config.name() == null || config.name().isBlank()) {
                errors.add(path + ".name: required");
            } else if (!names.add(config.name())) {
                errors.add(path + ".name: duplicate rule name '" + config.name() + "'");
            }
            errors.addAll(validate(config.rule(), config.params(), path));
        }
        return errors;
    }

    /** Validates one rule: its type exists, its params match the schema, and nested rules are valid. */
    public List<String> validate(String ruleName, JsonNode params, String path) {
        List<String> errors = new ArrayList<>();
        if (ruleName == null || ruleName.isBlank()) {
            errors.add(path + ".rule: required");
            return errors;
        }
        RuleType type = registry.find(ruleName).orElse(null);
        if (type == null) {
            errors.add(path + ".rule: unknown rule type '" + ruleName + "'");
            return errors;
        }
        String paramsPath = path + ".params";
        errors.addAll(type.schema().validate(params, paramsPath));
        if (!errors.isEmpty()) {
            return errors;
        }
        JsonNode withDefaults = type.schema().withDefaults(params);
        errors.addAll(type.validateParams(withDefaults, paramsPath));
        for (ParamSpec spec : type.schema().params()) {
            JsonNode value = withDefaults.get(spec.name());
            if (value == null) {
                continue;
            }
            String valuePath = paramsPath + "." + spec.name();
            if (spec.type() == ParamType.RULE) {
                errors.addAll(validateNode(value, valuePath));
            } else if (spec.type() == ParamType.RULE_LIST) {
                for (int i = 0; i < value.size(); i++) {
                    errors.addAll(validateNode(value.get(i), valuePath + "[" + i + "]"));
                }
            }
        }
        return errors;
    }

    /** Validates a nested rule object: {@code {"rule": "...", "params": {...}}}. */
    public List<String> validateNode(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            return List.of(path + ": must be an object with 'rule' and 'params'");
        }
        List<String> errors = new ArrayList<>();
        for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!name.equals("rule") && !name.equals("params")) {
                errors.add(path + "." + name + ": unknown field (nested rules have only 'rule' and 'params')");
            }
        }
        JsonNode rule = node.get("rule");
        errors.addAll(validate(rule != null && rule.isTextual() ? rule.textValue() : null, node.get("params"), path));
        return errors;
    }

    /** Builds a rule, validating it first. */
    public Rule create(String ruleName, JsonNode params) {
        List<String> errors = validate(ruleName, params, String.valueOf(ruleName));
        if (!errors.isEmpty()) {
            throw new InvalidConfigException(errors);
        }
        RuleType type = registry.find(ruleName).orElseThrow();
        return type.create(type.schema().withDefaults(params), this);
    }

    /** Builds a nested rule object: {@code {"rule": "...", "params": {...}}}. */
    public Rule createNode(JsonNode node) {
        return create(node.path("rule").asText(null), node.get("params"));
    }

    /** Builds the rule set of a strategy config. Throws {@link InvalidConfigException} if any rule is invalid. */
    public RuleSet buildRuleSet(List<RuleConfig> rules) {
        List<String> errors = validateRuleConfigs(rules);
        if (!errors.isEmpty()) {
            throw new InvalidConfigException(errors);
        }
        List<RuleSet.NamedRule> built = new ArrayList<>(rules.size());
        for (RuleConfig config : rules) {
            built.add(new RuleSet.NamedRule(config.name(), config.action(), create(config.rule(), config.params())));
        }
        return new RuleSet(built);
    }
}
