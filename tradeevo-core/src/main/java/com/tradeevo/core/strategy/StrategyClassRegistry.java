package com.tradeevo.core.strategy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;


/** The available {@link Strategy} implementations, by fully qualified class name. */
public class StrategyClassRegistry {

    public record StrategyClassInfo(String className, String description) {
    }

    private final Map<String, Strategy> strategies = new TreeMap<>();

    public StrategyClassRegistry(List<Strategy> strategies) {
        for (Strategy strategy : strategies) {
            this.strategies.put(userClass(strategy).getName(), strategy);
        }
    }

    /** The declared class, unwrapping runtime subclasses such as Spring CGLIB proxies ({@code Foo$$SpringCGLIB$$0}). */
    private static Class<?> userClass(Strategy strategy) {
        Class<?> type = strategy.getClass();
        while (type.getName().contains("$$") && type.getSuperclass() != null) {
            type = type.getSuperclass();
        }
        return type;
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
