package com.tradeevo.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeevo.core.error.NotFoundException;
import com.tradeevo.core.rule.ParamSpec;
import com.tradeevo.core.rule.RuleRegistry;
import com.tradeevo.core.rule.RuleType;
import com.tradeevo.core.strategy.StrategyClassRegistry;
import com.tradeevo.core.strategy.StrategyClassRegistry.StrategyClassInfo;

/** What configs can use: the core rule types (with parameter schemas) and the strategy classes. */
@RestController
@RequestMapping("/api")
public class CatalogController {

    public record RuleTypeInfo(String name, String description, List<ParamSpec> params) {

        static RuleTypeInfo of(RuleType type) {
            return new RuleTypeInfo(type.name(), type.description(), type.schema().params());
        }
    }

    private final RuleRegistry rules;
    private final StrategyClassRegistry strategies;

    public CatalogController(RuleRegistry rules, StrategyClassRegistry strategies) {
        this.rules = rules;
        this.strategies = strategies;
    }

    @GetMapping("/rules")
    public List<RuleTypeInfo> rules() {
        return rules.all().stream().map(RuleTypeInfo::of).toList();
    }

    @GetMapping("/rules/{name}")
    public RuleTypeInfo rule(@PathVariable String name) {
        return rules.find(name).map(RuleTypeInfo::of)
                .orElseThrow(() -> new NotFoundException("Rule type '" + name + "' not found"));
    }

    @GetMapping("/strategy-classes")
    public List<StrategyClassInfo> strategyClasses() {
        return strategies.all();
    }
}
