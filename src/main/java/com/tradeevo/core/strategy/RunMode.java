package com.tradeevo.core.strategy;

public enum RunMode {
    /** Data loaded from files over a date range; the result is stored. */
    BACKTEST,
    /** Data supplied in the request; the result is returned but not stored. */
    EVALUATION
}
