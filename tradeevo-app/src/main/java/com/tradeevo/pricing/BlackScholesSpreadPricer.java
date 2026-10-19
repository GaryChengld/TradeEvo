package com.tradeevo.pricing;

import java.time.LocalTime;

/** Short leg minus long leg, each priced by {@link ZeroDteOptionPricer}, clamped to {@code [0, width]}. */
public final class BlackScholesSpreadPricer implements SpreadPricer {

    private final ZeroDteOptionPricer options;

    public BlackScholesSpreadPricer(TimeParams time, BlackScholesParams params) {
        this.options = new ZeroDteOptionPricer(time, params);
    }

    @Override
    public double price(double spx, double vix, LocalTime now, SpreadDirection direction, double shortStrike,
            double longStrike) {
        Inputs.requireSpread(spx, vix, now, direction, shortStrike, longStrike);
        OptionType type = direction.optionType();
        double value = options.price(spx, shortStrike, vix, now, type) - options.price(spx, longStrike, vix, now, type);
        return Math.min(Math.max(value, 0), Math.abs(longStrike - shortStrike));
    }
}
