package com.tradeevo.pricing;

/**
 * @param ivMultiplier scales VIX into the volatility used: {@code sigma = VIX / 100 * ivMultiplier}
 * @param skew         raises volatility away from spot (see {@link ZeroDteOptionPricer}); 0 keeps it flat
 * @param rate         continuously compounded interest rate
 * @param dividend     continuous dividend yield
 */
public record BlackScholesParams(double ivMultiplier, double skew, double rate, double dividend) {

    public BlackScholesParams {
        ParamKind.POSITIVE.require("ivMultiplier", ivMultiplier);
        ParamKind.NON_NEGATIVE.require("skew", skew);
        ParamKind.FINITE.require("rate", rate);
        ParamKind.FINITE.require("dividend", dividend);
    }
}
