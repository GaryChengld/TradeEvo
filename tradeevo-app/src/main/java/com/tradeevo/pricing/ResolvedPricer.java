package com.tradeevo.pricing;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A pricer and the parameters it was built from, after overrides:
 * {@code {"model": ..., "time": {...}, "params": {...}}}. Strategies copy {@code parameters} into their
 * result's {@code assumptions} under {@code pricing}, so the run manifest records them and {@code compare}
 * reports runs priced with different parameters.
 */
public record ResolvedPricer(String model, SpreadPricer pricer, ObjectNode parameters) {

    public ResolvedPricer {
        parameters = parameters.deepCopy();
    }

    @Override
    public ObjectNode parameters() {
        return parameters.deepCopy();
    }
}
