package com.tradeevo.core.strategy;

import java.util.List;

/**
 * A strategy implementation. Each strategy defines its own raw-data JSON format, interprets its
 * rules' actions and settings, and computes its own metrics.
 *
 * <p>To add a strategy, create a Spring {@code @Component} implementing this interface under
 * {@code com.tradeevo.strategies.<name>} and reference its fully qualified class name in a config.
 * Implementations are singletons: keep per-run state in local variables, not fields, and do not use
 * randomness or wall-clock time, so that runs are reproducible.
 */
public interface Strategy {

    /** Runs the strategy over the data in the context. Throwing marks the run as FAILED. */
    StrategyResult run(StrategyRunContext context);

    /**
     * Strategy-specific config checks (required rule names or actions, settings), called after the
     * rules passed core validation. Return errors prefixed with their JSON path.
     */
    default List<String> validate(StrategyConfig config) {
        return List.of();
    }

    /** One line describing the strategy and its data format, shown in the catalog. */
    default String description() {
        return "";
    }
}
