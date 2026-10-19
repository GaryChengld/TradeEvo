package com.tradeevo.pricing;

/**
 * @param ivMultiplier scales VIX into the volatility used: {@code sigma = VIX / 100 * ivMultiplier}
 * @param rate         continuously compounded interest rate
 * @param dividend     continuous dividend yield
 */
public record BlackScholesParams(double ivMultiplier, double rate, double dividend) {

    public BlackScholesParams {
        ParamKind.POSITIVE.require("ivMultiplier", ivMultiplier);
        ParamKind.FINITE.require("rate", rate);
        ParamKind.FINITE.require("dividend", dividend);
    }
}
