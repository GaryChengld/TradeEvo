package com.tradeevo.core.rule;

/** What a top-level named rule does when it evaluates to {@link Truth#UNKNOWN}. */
public enum UnknownPolicy {
    /** Treat as not matching (default). Counted and reported in the run's rule stats. */
    NO_MATCH,
    /** Treat as matching. */
    MATCH,
    /** Fail the run. */
    FAIL
}
