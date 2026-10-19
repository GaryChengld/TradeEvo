package com.tradeevo.pricing;

/** The expected move EM and distances measured in it. Uses raw VIX, without any volatility multiplier. */
public final class ExpectedMove {

    private ExpectedMove() {
    }

    /** {@code EM = spx * vix / 100 * sqrt(years)}, in index points. */
    public static double points(double spx, double vix, double years) {
        return spx * (vix / 100) * Math.sqrt(years);
    }

    /** {@code N = D / EM}: how many expected moves the short strike is out of the money (negative if in). */
    public static double distance(double spx, double shortStrike, SpreadDirection direction, double em) {
        return direction.distance(spx, shortStrike) / em;
    }
}
