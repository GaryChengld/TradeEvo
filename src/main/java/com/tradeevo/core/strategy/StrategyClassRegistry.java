package com.tradeevo.core.strategy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/** All {@link Strategy} classes in this project, by fully qualified class name. */
@Component
public class StrategyClassRegistry {

    public record StrategyClassInfo(String className, String description) {
    }

    private final Map<String, Strategy> strategies = new TreeMap<>();

    public StrategyClassRegistry(List<Strategy> strategies) {
        for (Strategy strategy : strategies) {
            this.strategies.put(ClassUtils.getUserClass(strategy).getName(), strategy);
        }
    }

    public Optional<Strategy> find(String className) {
        return Optional.ofNullable(className).map(strategies::get);
    }

    public List<StrategyClassInfo> all() {
        return strategies.entrySet().stream()
                .map(entry -> new StrategyClassInfo(entry.getKey(), entry.getValue().description()))
                .toList();
    }
}
