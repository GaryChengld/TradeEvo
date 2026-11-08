package com.tradeevo.pricing;

import java.time.LocalTime;

/**
 * Black-Scholes price of a single SPX option expiring today, with
 * {@code sigma = VIX / 100 * ivMultiplier * (1 + skew * |strike / spx - 1| * 100)}: with a skew, volatility rises
 * by {@code skew} times the base for each percent the strike is away from spot, on either side. A skew of 0 gives
 * flat volatility.
 */
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
        double percentFromSpot = Math.abs(strike / spx - 1) * 100;
        double sigma = vix / 100 * params.ivMultiplier() * (1 + params.skew() * percentFromSpot);
        return BlackScholes.price(spx, strike, sigma, time.years(now), params.rate(), params.dividend(), type);
    }
}
