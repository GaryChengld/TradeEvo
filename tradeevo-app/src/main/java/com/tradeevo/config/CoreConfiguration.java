package com.tradeevo.config;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tradeevo.core.engine.StrategyEngine;
import com.tradeevo.core.rule.RuleFactory;
import com.tradeevo.core.rule.RuleRegistry;
import com.tradeevo.core.rule.RuleType;
import com.tradeevo.core.rule.types.CoreRuleTypes;
import com.tradeevo.core.strategy.Strategy;
import com.tradeevo.core.strategy.StrategyClassRegistry;

/**
 * Wires the framework-independent core into Spring. Strategy classes are discovered as {@link Strategy}
 * beans; custom {@link RuleType} beans are registered next to the built-in rules.
 */
@Configuration
public class CoreConfiguration {

    @Bean
    RuleRegistry ruleRegistry(List<RuleType> customRuleTypes) {
        List<RuleType> types = new ArrayList<>(CoreRuleTypes.builtIns());
        types.addAll(customRuleTypes);
        return new RuleRegistry(types);
    }

    @Bean
    RuleFactory ruleFactory(RuleRegistry ruleRegistry) {
        return new RuleFactory(ruleRegistry);
    }

    @Bean
    StrategyClassRegistry strategyClassRegistry(List<Strategy> strategies) {
        return new StrategyClassRegistry(strategies);
    }

    @Bean
    StrategyEngine strategyEngine(StrategyClassRegistry strategies, RuleFactory ruleFactory, Clock clock) {
        return new StrategyEngine(strategies, ruleFactory, clock);
    }
}
