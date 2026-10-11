package com.tradeevo.core.rule;

/** A built, parameterized rule. Must be stateless and deterministic. */
@FunctionalInterface
public interface Rule {

    /** TRUE or FALSE when decidable; UNKNOWN when needed facts are missing or not comparable. */
    Truth evaluate(Facts facts);
}
