package com.tradeevo.pricing;

import java.time.LocalTime;

/**
 * Time basis for 0DTE pricing. Annual variance is assigned to trading time only:
 * {@code years = minutesLeft / (tradingDaysPerYear * sessionMinutes)}.
 *
 * @param expiration         time of day the options expire, in exchange time (16:00, or 13:00 on early-close days)
 * @param tradingDaysPerYear trading days that carry a year's variance
 * @param sessionMinutes     trading minutes that carry one day's variance
 */
public record TimeParams(LocalTime expiration, int tradingDaysPerYear, int sessionMinutes) {

    public TimeParams {
        Inputs.requireNonNull("expiration", expiration);
        ParamKind.POSITIVE_INTEGER.require("tradingDaysPerYear", tradingDaysPerYear);
        ParamKind.POSITIVE_INTEGER.require("sessionMinutes", sessionMinutes);
    }
}
