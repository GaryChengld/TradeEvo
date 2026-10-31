package com.tradeevo.pricing;

import java.time.LocalTime;

/**
 * Calibrated credit-spread formula, with T = minutes left and N = D / EM (see {@link ExpectedMove}):
 * <pre>
 * C(T) = clamp(cScale * (T / cPivot)^cExponent, cMin, cMax)
 * M(T) = 1 + mHeight * exp(-(T - mCenter)^2 / (2 * mWidth^2))     midday stickiness
 * A(T) = 1 + aHeight * exp(-(T - aCenter)^2 / (2 * aWidth^2))     early-afternoon support
 * f(N) = min(width * base * exp(-decay * N) * C * M * A, width)
 * </pre>
 * <ul>
 * <li>T &lt;= 0: the intrinsic value {@code min(max(-D, 0), width)}.</li>
 * <li>N &gt;= 0 (out of the money): f(N).</li>
 * <li>N &lt; 0 (in the money): {@code max(intrinsic, width - (width - f(0)) * exp(-decay * |N|))}, which is
 * continuous at the strike and approaches the width deep in the money.</li>
 * </ul>
 *
 * <p>Known limits: the price scales linearly with width, so the model suits narrow spreads only (at 12:00 with
 * SPX 5000 and VIX 15, an at-the-money spread is worth more than the Black-Scholes value of its short leg once
 * it is wider than about 23 points). Exactly at the money the price holds at about 0.24 * width until the final
 * moment, then drops to 0 at expiry.
 */
public final class EmpiricalSpreadPricer implements SpreadPricer {

    private final ZeroDteTime time;
    private final EmpiricalParams p;

    public EmpiricalSpreadPricer(TimeParams time, EmpiricalParams params) {
        Inputs.requireNonNull("params", params);
        this.time = new ZeroDteTime(time);
        this.p = params;
    }

    @Override
    public double price(double spx, double vix, LocalTime now, SpreadDirection direction, double shortStrike,
            double longStrike) {
        Inputs.requireSpread(spx, vix, now, direction, shortStrike, longStrike);
        double width = Math.abs(longStrike - shortStrike);
        double intrinsic = direction.intrinsic(spx, shortStrike, longStrike);
        double t = time.minutesLeft(now);
        if (t <= 0) {
            return intrinsic;
        }
        double em = ExpectedMove.points(spx, vix, time.years(now));
        double n = ExpectedMove.distance(spx, shortStrike, direction, em);
        double c = Math.min(Math.max(p.cScale() * Math.pow(t / p.cPivot(), p.cExponent()), p.cMin()), p.cMax());
        double m = 1 + p.mHeight() * Math.exp(-Math.pow(t - p.mCenter(), 2) / (2 * p.mWidth() * p.mWidth()));
        double a = 1 + p.aHeight() * Math.exp(-Math.pow(t - p.aCenter(), 2) / (2 * p.aWidth() * p.aWidth()));
        double price;
        if (n >= 0) {
            price = Math.min(width * p.base() * Math.exp(-p.decay() * n) * c * m * a, width);
        } else {
            double atStrike = Math.min(width * p.base() * c * m * a, width);
            price = Math.max(intrinsic, width - (width - atStrike) * Math.exp(-p.decay() * -n));
        }
        Inputs.requireFinite("price", price);
        return price;
    }
}
