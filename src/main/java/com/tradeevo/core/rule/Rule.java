package com.tradeevo.core.rule;

/** A built, parameterized rule. Must be stateless and deterministic. */
@FunctionalInterface
public interface Rule {

    boolean evaluate(Facts facts);
}
