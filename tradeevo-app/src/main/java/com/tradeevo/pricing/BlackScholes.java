package com.tradeevo.pricing;

/**
 * European Black-Scholes price with continuous rate and dividend yield.
 *
 * <p>At or after expiry ({@code years <= 0}) the price is the intrinsic value. With {@code sigma = 0} it is
 * the discounted forward intrinsic value, which equals the intrinsic value when rate and dividend are 0.
 * Results are clamped to the no-arbitrage bounds, so rounding can never put a price below its lower
 * bound (e.g. a deep ITM call below intrinsic) or above its upper bound.
 */
public final class BlackScholes {

    private BlackScholes() {
    }

    public static double price(double spot, double strike, double sigma, double years, double rate, double dividend,
            OptionType type) {
        Inputs.requirePositive("spot", spot);
        Inputs.requirePositive("strike", strike);
        Inputs.requireFinite("years", years);
        Inputs.requireFinite("rate", rate);
        Inputs.requireFinite("dividend", dividend);
        if (!Double.isFinite(sigma) || sigma < 0) {
            throw new IllegalArgumentException("sigma: must be a finite number >= 0, was " + sigma);
        }
        if (years <= 0) {
            return type.intrinsic(spot, strike);
        }
        double forwardSpot = spot * Math.exp(-dividend * years);
        double discountedStrike = strike * Math.exp(-rate * years);
        double lower = type.intrinsic(forwardSpot, discountedStrike);
        double upper = type == OptionType.CALL ? forwardSpot : discountedStrike;
        if (sigma == 0) {
            return lower;
        }
        double volTime = sigma * Math.sqrt(years);
        double d1 = (Math.log(spot / strike) + (rate - dividend + sigma * sigma / 2) * years) / volTime;
        double d2 = d1 - volTime;
        double price = type == OptionType.CALL
                ? forwardSpot * NormalDistribution.cdf(d1) - discountedStrike * NormalDistribution.cdf(d2)
                : discountedStrike * NormalDistribution.cdf(-d2) - forwardSpot * NormalDistribution.cdf(-d1);
        return Math.min(Math.max(price, lower), upper);
    }
}
