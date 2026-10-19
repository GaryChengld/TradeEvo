package com.tradeevo.pricing;

import java.time.LocalTime;

/** Black-Scholes price of a single SPX option expiring today, with {@code sigma = VIX / 100 * ivMultiplier}. */
public final class ZeroDteOptionPricer {

    private final ZeroDteTime time;
    private final BlackScholesParams params;

    public ZeroDteOptionPricer(TimeParams time, BlackScholesParams params) {
        Inputs.requireNonNull("params", params);
        this.time = new ZeroDteTime(time);
        this.params = params;
    }

    public double price(double spx, double strike, double vix, LocalTime now, OptionType type) {
        Inputs.requirePositive("spx", spx);
        Inputs.requirePositive("strike", strike);
        Inputs.requirePositive("vix", vix);
        Inputs.requireNonNull("type", type);
        double sigma = vix / 100 * params.ivMultiplier();
        return BlackScholes.price(spx, strike, sigma, time.years(now), params.rate(), params.dividend(), type);
    }
}
