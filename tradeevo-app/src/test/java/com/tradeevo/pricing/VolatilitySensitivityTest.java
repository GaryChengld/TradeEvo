package com.tradeevo.pricing;

import static com.tradeevo.pricing.OptionType.CALL;
import static com.tradeevo.pricing.PricingFixtures.BS_SPREADS;
import static com.tradeevo.pricing.PricingFixtures.EMPIRICAL_SPREADS;
import static com.tradeevo.pricing.PricingFixtures.OPTIONS;
import static com.tradeevo.pricing.PricingFixtures.SPX;
import static com.tradeevo.pricing.PricingFixtures.TEN;
import static com.tradeevo.pricing.PricingFixtures.TIME;
import static com.tradeevo.pricing.PricingFixtures.VIX;
import static com.tradeevo.pricing.SpreadDirection.BULL_PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class VolatilitySensitivityTest {

    private static final double[] VIX_LEVELS = {5, 10, 15, 25, 40, 80};

    private static double vega(double strike, LocalTime now, OptionType type) {
        return (OPTIONS.price(SPX, strike, VIX + 0.01, now, type) - OPTIONS.price(SPX, strike, VIX - 0.01, now, type)) / 0.02;
    }

    @Test
    void optionPricesRiseWithVix() {
        for (double strike : new double[] {4980, 5000, 5020}) {
            for (OptionType type : OptionType.values()) {
                double previous = -1;
                for (double vix : VIX_LEVELS) {
                    double price = OPTIONS.price(SPX, strike, vix, TEN, type);
                    assertThat(price).as("%s K=%s vix=%s", type, strike, vix).isGreaterThan(previous);
                    previous = price;
                }
            }
        }
    }

    @Test
    void vegaIsPositiveAndLargestNearTheMoney() {
        double twoEm = 2 * PricingFixtures.em(SPX, VIX, TEN);
        for (OptionType type : OptionType.values()) {
            double atm = vega(SPX, TEN, type);
            assertThat(atm).isPositive();
            assertThat(atm).isGreaterThan(vega(SPX + twoEm, TEN, type)).isGreaterThan(vega(SPX - twoEm, TEN, type));
        }
    }

    @Test
    void lowVolatilityPricesAreIntrinsic() {
        for (double strike : new double[] {4990, 5010}) {
            for (OptionType type : OptionType.values()) {
                assertThat(OPTIONS.price(SPX, strike, 0.01, TEN, type))
                        .isCloseTo(type.intrinsic(SPX, strike), within(1e-6));
            }
        }
    }

    @Test
    void vegaShrinksAsExpirationNears() {
        assertThat(vega(SPX, LocalTime.of(15, 30), CALL)).isPositive().isLessThan(vega(SPX, TEN, CALL));
    }

    @Test
    void multiplierEqualsScalingVix() {
        for (double k : new double[] {0.5, 1.0, 1.15, 2.0}) {
            ZeroDteOptionPricer scaled = new ZeroDteOptionPricer(TIME, new BlackScholesParams(k, 0, 0));
            for (double strike : new double[] {4950, 5000, 5050}) {
                for (OptionType type : OptionType.values()) {
                    assertThat(scaled.price(SPX, strike, VIX, TEN, type)).as("k=%s K=%s %s", k, strike, type)
                            .isCloseTo(OPTIONS.price(SPX, strike, VIX * k, TEN, type), within(1e-9));
                }
            }
        }
    }

    @Test
    void blackScholesSpreadValueRisesWithVixOutOfTheMoneyAndFallsInTheMoney() {
        double em = PricingFixtures.em(SPX, VIX, TEN);
        double otmShort = SPX - em;
        double itmLong = SPX + em;
        double previousOtm = -1;
        double previousItm = Double.POSITIVE_INFINITY;
        for (double vix : new double[] {5, 10, 15, 25, 40}) {
            double otm = BS_SPREADS.price(SPX, vix, TEN, BULL_PUT, otmShort, otmShort - 10);
            double itm = BS_SPREADS.price(SPX, vix, TEN, BULL_PUT, itmLong + 25, itmLong);
            assertThat(otm).as("OTM vix=%s", vix).isGreaterThan(previousOtm);
            assertThat(itm).as("ITM vix=%s", vix).isLessThan(previousItm);
            previousOtm = otm;
            previousItm = itm;
        }
    }

    @Test
    void empiricalSpreadValueRisesWithVixOutOfTheMoney() {
        double shortStrike = SPX - PricingFixtures.em(SPX, VIX, TEN);
        double previous = -1;
        for (double vix : new double[] {5, 10, 15, 25, 40}) {
            double price = EMPIRICAL_SPREADS.price(SPX, vix, TEN, BULL_PUT, shortStrike, shortStrike - 10);
            assertThat(price).as("vix=%s", vix).isGreaterThan(previous);
            previous = price;
        }
    }
}
