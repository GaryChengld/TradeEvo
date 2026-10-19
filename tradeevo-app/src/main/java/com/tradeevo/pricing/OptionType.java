package com.tradeevo.pricing;

public enum OptionType {
    CALL, PUT;

    /** Payoff at expiry: {@code max(spot - strike, 0)} for a call, {@code max(strike - spot, 0)} for a put. */
    public double intrinsic(double spot, double strike) {
        return this == CALL ? Math.max(spot - strike, 0) : Math.max(strike - spot, 0);
    }
}
