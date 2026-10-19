package com.tradeevo.pricing;

import static com.tradeevo.pricing.PricingFixtures.CLOSE;
import static com.tradeevo.pricing.PricingFixtures.EMPIRICAL_SPREADS;
import static com.tradeevo.pricing.PricingFixtures.NOON;
import static com.tradeevo.pricing.PricingFixtures.SPX;
import static com.tradeevo.pricing.PricingFixtures.VIX;
import static com.tradeevo.pricing.SpreadDirection.BEAR_CALL;
import static com.tradeevo.pricing.SpreadDirection.BULL_PUT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class EmpiricalSpreadPricerTest {

    /** Direct port of the original TypeScript {@code spreadPricing} function, with its constants. */
    static double original(double spx, SpreadDirection direction, double shortStrike, double longStrike, double vix,
            double remainingHours) {
        double t = remainingHours * 60;
        if (t <= 0) {
            return 0;
        }
        double width = Math.abs(longStrike - shortStrike);
        double em = spx * (vix / 100) * Math.sqrt(t / (252 * 390));
        double d = direction == BEAR_CALL ? shortStrike - spx : spx - shortStrike;
        double n = d / em;
        if (n < 0) {
            return Math.max(0, Math.min(-d, width));
        }
        double cT = Math.min(Math.max(1.34 * Math.pow(t / 138, 0.45), 0.8), 2.1);
        double mT = 1 + 0.22 * Math.exp(-Math.pow(t - 240, 2) / (2 * 55 * 55));
        double aT = 1 + 0.22 * Math.exp(-Math.pow(t - 150, 2) / (2 * 45 * 45));
        return Math.min(width * 0.3 * Math.exp(-3.8 * n) * cT * mT * aT, width);
    }

    @Test
    void outOfTheMoneyMatchesTheOriginalFunction() {
        int checked = 0;
        for (LocalTime now : new LocalTime[] {LocalTime.of(9, 45), LocalTime.of(11, 0), NOON, LocalTime.of(13, 30),
                LocalTime.of(15, 0), LocalTime.of(15, 55)}) {
            double hours = java.time.Duration.between(now, CLOSE).toMinutes() / 60.0;
            for (double distance : new double[] {0, 5, 10, 25, 50, 100}) {
                for (double width : new double[] {5, 10, 25}) {
                    for (double vix : new double[] {12, VIX, 30}) {
                        double put = SPX - distance;
                        double call = SPX + distance;
                        assertClose(EMPIRICAL_SPREADS.price(SPX, vix, now, BULL_PUT, put, put - width),
                                original(SPX, BULL_PUT, put, put - width, vix, hours));
                        assertClose(EMPIRICAL_SPREADS.price(SPX, vix, now, BEAR_CALL, call, call + width),
                                original(SPX, BEAR_CALL, call, call + width, vix, hours));
                        checked += 2;
                    }
                }
            }
        }
        assertThat(checked).isEqualTo(648);
    }

    private static void assertClose(double actual, double expected) {
        assertThat(Math.abs(actual - expected)).as("%s vs original %s", actual, expected)
                .isLessThanOrEqualTo(1e-12 * Math.max(1, Math.abs(expected)));
    }

    @Test
    void isContinuousAtTheStrike() {
        for (SpreadDirection direction : SpreadDirection.values()) {
            double sign = direction == BULL_PUT ? 1 : -1;
            double otm = SPX - sign * 1e-10;
            double itm = SPX + sign * 1e-10;
            double width = 10;
            double outside = EMPIRICAL_SPREADS.price(SPX, VIX, NOON, direction, otm, PricingFixtures.longStrike(direction, otm, width));
            double inside = EMPIRICAL_SPREADS.price(SPX, VIX, NOON, direction, itm, PricingFixtures.longStrike(direction, itm, width));
            assertThat(Math.abs(outside - inside)).as(direction.name()).isLessThan(1e-9);
        }
    }

    @Test
    void atOrAfterExpiryReturnsIntrinsic() {
        for (LocalTime now : new LocalTime[] {CLOSE, LocalTime.of(16, 30)}) {
            assertThat(EMPIRICAL_SPREADS.price(SPX, VIX, now, BULL_PUT, 4990, 4980)).as("OTM").isEqualTo(0);
            assertThat(EMPIRICAL_SPREADS.price(SPX, VIX, now, BULL_PUT, 5020, 5010)).as("fully ITM").isEqualTo(10);
            assertThat(EMPIRICAL_SPREADS.price(SPX, VIX, now, BULL_PUT, 5004, 4994)).as("partly ITM").isEqualTo(4);
            assertThat(EMPIRICAL_SPREADS.price(SPX, VIX, now, BEAR_CALL, 4996, 5006)).as("partly ITM").isEqualTo(4);
        }
    }

    @Test
    void staysBetweenIntrinsicAndWidthAndFallsAsTheStrikeMovesOut() {
        double em = PricingFixtures.em(SPX, VIX, NOON);
        for (SpreadDirection direction : SpreadDirection.values()) {
            double previous = Double.POSITIVE_INFINITY;
            for (double n = -4; n <= 4; n += 0.05) {
                double shortStrike = PricingFixtures.shortStrikeAt(direction, SPX, n, em);
                double longStrike = PricingFixtures.longStrike(direction, shortStrike, 10);
                double price = EMPIRICAL_SPREADS.price(SPX, VIX, NOON, direction, shortStrike, longStrike);
                assertThat(price).as("%s n=%s", direction, n)
                        .isBetween(direction.intrinsic(SPX, shortStrike, longStrike), 10.0)
                        .isLessThanOrEqualTo(previous);
                if (n > 0) {
                    assertThat(price).as("strictly decreasing out of the money, n=%s", n).isLessThan(previous);
                }
                previous = price;
            }
        }
    }
}
