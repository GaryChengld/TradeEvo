package com.tradeevo.pricing;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Time left until today's expiration, on a trading-time basis.
 *
 * <p>Known simplifications: the session carries the whole day's variance (the overnight share, roughly
 * 20–25%, has already passed by the open, so this slightly overstates the variance left), and variance is
 * uniform within the day (the open, the close and events such as FOMC are not weighted).
 */
public final class ZeroDteTime {

    private static final double NANOS_PER_MINUTE = 60e9;

    private final TimeParams params;

    public ZeroDteTime(TimeParams params) {
        Inputs.requireNonNull("params", params);
        this.params = params;
    }

    /** Minutes from {@code now} to expiration, with seconds as fractions; 0 at or after expiration. */
    public double minutesLeft(LocalTime now) {
        Inputs.requireNonNull("now", now);
        long nanos = Duration.between(now, params.expiration()).toNanos();
        return nanos <= 0 ? 0 : nanos / NANOS_PER_MINUTE;
    }

    /** Years left, where one year is {@code tradingDaysPerYear * sessionMinutes} trading minutes. */
    public double years(LocalTime now) {
        return minutesLeft(now) / ((double) params.tradingDaysPerYear() * params.sessionMinutes());
    }
}
