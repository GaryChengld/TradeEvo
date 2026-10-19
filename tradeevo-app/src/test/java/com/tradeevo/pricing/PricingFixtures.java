package com.tradeevo.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inline parameters and helpers for pricing tests, so the tests don't depend on the shipped parameters file.
 * The values equal the shipped ones; {@code PricingModelsTest} checks the file itself.
 */
final class PricingFixtures {

    /** A simulated price is acceptable within ±20% of a market-based reference value. */
    static final double TOLERANCE = 0.20;

    static final double SPX = 5000;
    static final double VIX = 15;
    static final LocalTime TEN = LocalTime.of(10, 0);
    static final LocalTime NOON = LocalTime.of(12, 0);
    static final LocalTime CLOSE = LocalTime.of(16, 0);

    static final TimeParams TIME = new TimeParams(CLOSE, 252, 390);
    static final BlackScholesParams BS = new BlackScholesParams(1.0, 0.0, 0.0);
    static final EmpiricalParams EMPIRICAL = new EmpiricalParams(0.3, 3.8, 1.34, 138, 0.45, 0.8, 2.1,
            0.22, 240, 55, 0.22, 150, 45);

    static final ZeroDteOptionPricer OPTIONS = new ZeroDteOptionPricer(TIME, BS);
    static final SpreadPricer BS_SPREADS = new BlackScholesSpreadPricer(TIME, BS);
    static final SpreadPricer EMPIRICAL_SPREADS = new EmpiricalSpreadPricer(TIME, EMPIRICAL);

    static final Map<String, SpreadPricer> BOTH_MODELS = new LinkedHashMap<>();

    static {
        BOTH_MODELS.put("black-scholes", BS_SPREADS);
        BOTH_MODELS.put("empirical", EMPIRICAL_SPREADS);
    }

    private PricingFixtures() {
    }

    /** Asserts {@code |actual - reference| <= 20% of |reference|}; only for market-based references. */
    static void assertWithinTolerance(double actual, double reference) {
        assertThat(Math.abs(actual - reference))
                .as("%s should be within ±%.0f%% of %s", actual, TOLERANCE * 100, reference)
                .isLessThanOrEqualTo(TOLERANCE * Math.abs(reference));
    }

    /** Expected move in points with the fixture time basis. */
    static double em(double spx, double vix, LocalTime now) {
        return ExpectedMove.points(spx, vix, new ZeroDteTime(TIME).years(now));
    }

    /** The short strike that is {@code n} expected moves out of the money (negative: in the money). */
    static double shortStrikeAt(SpreadDirection direction, double spx, double n, double em) {
        return direction == SpreadDirection.BULL_PUT ? spx - n * em : spx + n * em;
    }

    /** The long strike {@code width} points further out of the money than the short strike. */
    static double longStrike(SpreadDirection direction, double shortStrike, double width) {
        return direction == SpreadDirection.BULL_PUT ? shortStrike - width : shortStrike + width;
    }
}
