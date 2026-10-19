package com.tradeevo.pricing;

import static com.tradeevo.pricing.OptionType.CALL;
import static com.tradeevo.pricing.OptionType.PUT;
import static com.tradeevo.pricing.PricingFixtures.BOTH_MODELS;
import static com.tradeevo.pricing.PricingFixtures.OPTIONS;
import static com.tradeevo.pricing.PricingFixtures.SPX;
import static com.tradeevo.pricing.PricingFixtures.TEN;
import static com.tradeevo.pricing.PricingFixtures.VIX;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/** Deep in-the-money and out-of-the-money options and spreads, extreme inputs and no-arbitrage bounds. */
class MoneynessTest {

    @Test
    void deepInTheMoneyIsWorthIntrinsic() {
        assertThat(PricingFixtures.em(SPX, VIX, TEN)).as("300 points is about 7 EM").isBetween(40.0, 46.0);
        double call = OPTIONS.price(SPX, 4700, VIX, TEN, CALL);
        double put = OPTIONS.price(SPX, 5300, VIX, TEN, PUT);

        assertThat(call - 300).isBetween(0.0, 1e-6);
        assertThat(put - 300).isBetween(0.0, 1e-6);
    }

    @Test
    void deepOutOfTheMoneyIsWorthAlmostNothing() {
        assertThat(OPTIONS.price(SPX, 5300, VIX, TEN, CALL)).isBetween(0.0, 1e-6);
        assertThat(OPTIONS.price(SPX, 4700, VIX, TEN, PUT)).isBetween(0.0, 1e-6);
    }

    @Test
    void extremeInputsGiveFiniteResults() {
        for (double strike : new double[] {1, SPX * 10}) {
            for (double vix : new double[] {0.01, VIX, 200}) {
                for (LocalTime now : new LocalTime[] {TEN, LocalTime.of(15, 59, 59)}) {
                    for (OptionType type : OptionType.values()) {
                        assertThat(OPTIONS.price(SPX, strike, vix, now, type))
                                .as("%s K=%s vix=%s %s", type, strike, vix, now).isFinite().isNotNegative();
                    }
                }
            }
        }
    }

    @Test
    void respectsNoArbitrageBounds() {
        for (double strike : new double[] {1, 4000, 4900, 4990, 5000, 5010, 5100, 6000, 50_000}) {
            for (double vix : new double[] {0.01, 5, VIX, 80, 200}) {
                for (LocalTime now : new LocalTime[] {LocalTime.of(9, 30), LocalTime.NOON, LocalTime.of(15, 59, 59)}) {
                    double call = OPTIONS.price(SPX, strike, vix, now, CALL);
                    double put = OPTIONS.price(SPX, strike, vix, now, PUT);
                    String label = "K=" + strike + " vix=" + vix + " " + now;
                    assertThat(call).as(label).isBetween(Math.max(SPX - strike, 0), SPX);
                    assertThat(put).as(label).isBetween(Math.max(strike - SPX, 0), strike);
                }
            }
        }
    }

    @Test
    void spreadsFiveExpectedMovesOutOrInAreWorthNothingOrTheWidth() {
        double em = PricingFixtures.em(SPX, VIX, TEN);
        double width = 10;
        BOTH_MODELS.forEach((model, pricer) -> {
            for (SpreadDirection direction : SpreadDirection.values()) {
                double farOut = PricingFixtures.shortStrikeAt(direction, SPX, 5, em);
                assertThat(pricer.price(SPX, VIX, TEN, direction, farOut, PricingFixtures.longStrike(direction, farOut, width)))
                        .as("%s %s 5 EM OTM", model, direction).isBetween(0.0, 1e-3 * width);

                // Long strike 5 EM in the money, so the short strike is even deeper.
                double longIn = PricingFixtures.shortStrikeAt(direction, SPX, -5, em);
                double shortIn = direction == SpreadDirection.BULL_PUT ? longIn + width : longIn - width;
                assertThat(pricer.price(SPX, VIX, TEN, direction, shortIn, longIn))
                        .as("%s %s long 5 EM ITM", model, direction).isBetween((1 - 1e-3) * width, width);
            }
        });
    }
}
