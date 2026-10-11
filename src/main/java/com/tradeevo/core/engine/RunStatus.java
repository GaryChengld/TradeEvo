package com.tradeevo.core.engine;

public enum RunStatus {
    COMPLETED,
    /** The strategy threw while running; see {@link RunRecord#error()}. */
    FAILED
}
