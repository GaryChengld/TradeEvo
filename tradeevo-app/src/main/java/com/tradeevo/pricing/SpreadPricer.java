package com.tradeevo.pricing;

import java.time.LocalTime;

/** Prices an SPX 0DTE credit spread. Implementations are pure and deterministic. */
public interface SpreadPricer {

    /**
     * The spread's value to close (what it costs to buy back), between 0 and the width.
     *
     * @throws IllegalArgumentException if SPX, VIX or a strike is not finite and positive, or the strikes
     *                                  are in the wrong order for the direction
     */
    double price(double spx, double vix, LocalTime now, SpreadDirection direction, double shortStrike,
            double longStrike);
}
