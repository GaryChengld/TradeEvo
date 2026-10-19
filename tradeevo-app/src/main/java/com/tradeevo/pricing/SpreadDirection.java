package com.tradeevo.pricing;

/** A credit spread: sell the short strike, buy the long strike further out of the money. */
public enum SpreadDirection {
    /** Short put spread: long strike below the short strike. */
    BULL_PUT,
    /** Short call spread: long strike above the short strike. */
    BEAR_CALL;

    public OptionType optionType() {
        return this == BULL_PUT ? OptionType.PUT : OptionType.CALL;
    }

    /** Distance D from SPX to the short strike, in index points, positive when out of the money. */
    public double distance(double spx, double shortStrike) {
        return this == BULL_PUT ? spx - shortStrike : shortStrike - spx;
    }

    /** Value at expiry, per unit: {@code min(max(-D, 0), width)}. */
    public double intrinsic(double spx, double shortStrike, double longStrike) {
        return Math.min(Math.max(-distance(spx, shortStrike), 0), Math.abs(longStrike - shortStrike));
    }

    void requireStrikeOrder(double shortStrike, double longStrike) {
        boolean ok = this == BULL_PUT ? longStrike < shortStrike : longStrike > shortStrike;
        if (!ok) {
            throw new IllegalArgumentException("longStrike: must be " + (this == BULL_PUT ? "below" : "above")
                    + " shortStrike for " + this + ", was " + longStrike + " vs " + shortStrike);
        }
    }
}
