package com.tradeevo.pricing;

/** Call-time input checks shared by the pricers. Violations throw {@link IllegalArgumentException}. */
final class Inputs {

    private Inputs() {
    }

    static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + ": must be a finite number > 0, was " + value);
        }
    }

    static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + ": must be a finite number, was " + value);
        }
    }

    static void requireNonNull(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(name + ": is required");
        }
    }

    /** Checks the inputs every spread pricer takes. */
    static void requireSpread(double spx, double vix, Object now, SpreadDirection direction, double shortStrike,
            double longStrike) {
        requirePositive("spx", spx);
        requirePositive("vix", vix);
        requireNonNull("now", now);
        requireNonNull("direction", direction);
        requirePositive("shortStrike", shortStrike);
        requirePositive("longStrike", longStrike);
        direction.requireStrikeOrder(shortStrike, longStrike);
    }
}
