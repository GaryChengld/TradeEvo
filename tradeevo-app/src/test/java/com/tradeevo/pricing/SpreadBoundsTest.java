package com.tradeevo.pricing;

import static com.tradeevo.pricing.PricingFixtures.BOTH_MODELS;
import static com.tradeevo.pricing.PricingFixtures.BS_SPREADS;
import static com.tradeevo.pricing.PricingFixtures.EMPIRICAL_SPREADS;
import static com.tradeevo.pricing.PricingFixtures.NOON;
import static com.tradeevo.pricing.PricingFixtures.OPTIONS;
import static com.tradeevo.pricing.PricingFixtures.SPX;
import static com.tradeevo.pricing.PricingFixtures.TEN;
import static com.tradeevo.pricing.PricingFixtures.VIX;
import static com.tradeevo.pricing.SpreadDirection.BULL_PUT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/** Property tests over a fixed grid: no randomness, so a failure always reproduces. */
class SpreadBoundsTest {

    private static final double[] SPX_LEVELS = {4000, 5000, 6000};
    private static final double[] VIX_LEVELS = {5, 15, 40, 80};
    private static final LocalTime[] TIMES = {LocalTime.of(9, 30), NOON, LocalTime.of(15, 30), LocalTime.of(15, 59),
            LocalTime.of(16, 0), LocalTime.of(16, 30)};
    private static final double[] WIDTHS = {5, 25, 100};
    private static final double[] DISTANCES = {-3, -1, -0.25, 0, 0.25, 1, 3};

    @Test
    void everyGridPointIsFiniteAndWithinZeroAndWidth() {
        int checked = 0;
        for (var model : BOTH_MODELS.entrySet()) {
            for (SpreadDirection direction : SpreadDirection.values()) {
                for (double spx : SPX_LEVELS) {
                    for (double vix : VIX_LEVELS) {
                        // Strikes are placed with the noon expected move, so they stay distinct after the close.
                        double em = PricingFixtures.em(spx, vix, NOON);
                        for (LocalTime now : TIMES) {
                            for (double width : WIDTHS) {
                                for (double n : DISTANCES) {
                                    double shortStrike = PricingFixtures.shortStrikeAt(direction, spx, n, em);
                                    double longStrike = PricingFixtures.longStrike(direction, shortStrike, width);
                                    double price = model.getValue().price(spx, vix, now, direction, shortStrike, longStrike);
                                    String label = model.getKey() + " " + direction + " spx=" + spx + " vix=" + vix + " "
                                            + now + " width=" + width + " n=" + n;
                                    assertThat(price).as(label).isFinite().isBetween(0.0, width);
                                    if (model.getValue() == EMPIRICAL_SPREADS) {
                                        assertThat(price).as(label)
                                                .isGreaterThanOrEqualTo(direction.intrinsic(spx, shortStrike, longStrike));
                                    }
                                    checked++;
                                }
                            }
                        }
                    }
                }
            }
        }
        assertThat(checked).isEqualTo(2 * 2 * 3 * 4 * 6 * 3 * 7);
    }

    @Test
    void valueDoesNotRiseAsSpxMovesAwayFromTheShortStrike() {
        BOTH_MODELS.forEach((model, pricer) -> {
            double previousPut = Double.POSITIVE_INFINITY;
            double previousCall = Double.POSITIVE_INFINITY;
            for (double step = 0; step <= 150; step += 2.5) {
                double put = pricer.price(4950 + step, VIX, NOON, BULL_PUT, 5000, 4990);
                double call = pricer.price(5050 - step, VIX, NOON, SpreadDirection.BEAR_CALL, 5000, 5010);
                assertThat(put).as("%s bull put, spx=%s", model, 4950 + step).isLessThanOrEqualTo(previousPut);
                assertThat(call).as("%s bear call, spx=%s", model, 5050 - step).isLessThanOrEqualTo(previousCall);
                previousPut = put;
                previousCall = call;
            }
        });
    }

    @Test
    void wideningTheSpreadAddsAtMostOnePointPerPoint() {
        double em = PricingFixtures.em(SPX, VIX, NOON);
        BOTH_MODELS.forEach((model, pricer) -> {
            for (SpreadDirection direction : SpreadDirection.values()) {
                for (double n : new double[] {-1, -0.25, 0, 0.5, 1.5}) {
                    double shortStrike = PricingFixtures.shortStrikeAt(direction, SPX, n, em);
                    double previous = 0;
                    for (double width = 1; width <= 60; width++) {
                        double price = pricer.price(SPX, VIX, NOON, direction, shortStrike,
                                PricingFixtures.longStrike(direction, shortStrike, width));
                        String label = model + " " + direction + " n=" + n + " width=" + width;
                        assertThat(price).as(label).isGreaterThanOrEqualTo(previous - 1e-12)
                                .isLessThanOrEqualTo(previous + 1 + 1e-12);
                        previous = price;
                    }
                }
            }
        });
    }

    @Test
    void blackScholesSpreadIsWorthNoMoreThanItsShortLeg() {
        double em = PricingFixtures.em(SPX, VIX, TEN);
        for (SpreadDirection direction : SpreadDirection.values()) {
            for (double n : DISTANCES) {
                double shortStrike = PricingFixtures.shortStrikeAt(direction, SPX, n, em);
                double spread = BS_SPREADS.price(SPX, VIX, TEN, direction, shortStrike,
                        PricingFixtures.longStrike(direction, shortStrike, 25));
                assertThat(spread).isLessThanOrEqualTo(OPTIONS.price(SPX, shortStrike, VIX, TEN, direction.optionType()));
            }
        }
    }

    @Test
    void blackScholesIsSmoothAtTheStrike() {
        double em = PricingFixtures.em(SPX, VIX, NOON);
        for (SpreadDirection direction : SpreadDirection.values()) {
            double in = PricingFixtures.shortStrikeAt(direction, SPX, -1e-6, em);
            double out = PricingFixtures.shortStrikeAt(direction, SPX, 1e-6, em);
            double width = 10;
            double gap = BS_SPREADS.price(SPX, VIX, NOON, direction, in, PricingFixtures.longStrike(direction, in, width))
                    - BS_SPREADS.price(SPX, VIX, NOON, direction, out, PricingFixtures.longStrike(direction, out, width));
            assertThat(Math.abs(gap)).isLessThanOrEqualTo(1e-3 * width);
        }
    }
}
