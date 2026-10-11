package com.tradeevo.core.rule;

/**
 * Three-valued rule outcome (Kleene logic). UNKNOWN means the facts needed to decide were missing or
 * not comparable; it is never silently turned into FALSE inside a rule, so {@code Not} of an
 * unknown condition stays unknown.
 */
public enum Truth {
    TRUE,
    FALSE,
    UNKNOWN;

    public static Truth of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public Truth not() {
        return switch (this) {
            case TRUE -> FALSE;
            case FALSE -> TRUE;
            case UNKNOWN -> UNKNOWN;
        };
    }

    /** FALSE dominates, then UNKNOWN. */
    public Truth and(Truth other) {
        if (this == FALSE || other == FALSE) {
            return FALSE;
        }
        return this == UNKNOWN || other == UNKNOWN ? UNKNOWN : TRUE;
    }

    /** TRUE dominates, then UNKNOWN. */
    public Truth or(Truth other) {
        if (this == TRUE || other == TRUE) {
            return TRUE;
        }
        return this == UNKNOWN || other == UNKNOWN ? UNKNOWN : FALSE;
    }
}
