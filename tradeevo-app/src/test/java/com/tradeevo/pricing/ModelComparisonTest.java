package com.tradeevo.pricing;

import static com.tradeevo.pricing.PricingFixtures.NOON;
import static com.tradeevo.pricing.PricingFixtures.SPX;
import static com.tradeevo.pricing.PricingFixtures.VIX;
import static com.tradeevo.pricing.SpreadDirection.BULL_PUT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tradeevo.AppTestSupport;

/**
 * Pins both models' prices with the shipped parameters file, for a 5-wide bull put spread at 12:00
 * (SPX 5000, VIX 15). It documents the gap between the models and fails on any change to either formula
 * or to the file. After an intended parameter change, update the pinned values.
 */
class ModelComparisonTest {

    private static final double WIDTH = 5;

    /** N (expected moves out of the money), Black-Scholes price, empirical price. */
    private static final double[][] PINNED = {
            {0, 2.372979844166821, 3.2392702335431456},
            {0.5, 1.4310038605897262, 0.4844931761200479},
            {1, 0.71473033537211, 0.07246497537506028},
            {1.28, 0.4429542074195183, 0.02500568869682424}};

    @Test
    void pricesWithTheShippedParametersArePinned() {
        PricingModels models = PricingModels.load(AppTestSupport.PRICING_FILE, AppTestSupport.MAPPER);
        SpreadPricer blackScholes = models.resolve(null, "pricing").pricer();
        SpreadPricer empirical = new EmpiricalSpreadPricer(models.timeParams(), models.empiricalParams());
        double em = ExpectedMove.points(SPX, VIX, new ZeroDteTime(models.timeParams()).years(NOON));

        for (double[] row : PINNED) {
            double shortStrike = SPX - row[0] * em;
            double bs = blackScholes.price(SPX, VIX, NOON, BULL_PUT, shortStrike, shortStrike - WIDTH);
            double emp = empirical.price(SPX, VIX, NOON, BULL_PUT, shortStrike, shortStrike - WIDTH);
            assertThat(Math.abs(bs - row[1])).as("Black-Scholes at N=%s: %s", row[0], bs).isLessThan(1e-12);
            assertThat(Math.abs(emp - row[2])).as("empirical at N=%s: %s", row[0], emp).isLessThan(1e-12);
        }
    }

    @Test
    void documentsTheGapBetweenTheModels() {
        // At the strike the empirical model is richer; from 0.5 EM out it is much cheaper.
        assertThat(PINNED[0][2]).isGreaterThan(PINNED[0][1]);
        assertThat(PINNED[2][1] / PINNED[2][2]).as("about 10x apart at N = 1").isBetween(8.0, 12.0);
        assertThat(PINNED[3][1] / PINNED[3][2]).as("about 18x apart at N = 1.28").isBetween(15.0, 20.0);
    }
}
