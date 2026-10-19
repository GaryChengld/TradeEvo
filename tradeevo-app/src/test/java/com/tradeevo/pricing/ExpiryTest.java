package com.tradeevo.pricing;

import static com.tradeevo.pricing.OptionType.CALL;
import static com.tradeevo.pricing.OptionType.PUT;
import static com.tradeevo.pricing.PricingFixtures.BOTH_MODELS;
import static com.tradeevo.pricing.PricingFixtures.BS;
import static com.tradeevo.pricing.PricingFixtures.CLOSE;
import static com.tradeevo.pricing.PricingFixtures.EMPIRICAL;
import static com.tradeevo.pricing.PricingFixtures.OPTIONS;
import static com.tradeevo.pricing.PricingFixtures.SPX;
import static com.tradeevo.pricing.PricingFixtures.VIX;
import static com.tradeevo.pricing.SpreadDirection.BEAR_CALL;
import static com.tradeevo.pricing.SpreadDirection.BULL_PUT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExpiryTest {

    private static final double[] STRIKES = {4950, 4990, 5000, 5010, 5050};

    @Test
    void atAndAfterExpirationOptionsAreWorthIntrinsic() {
        for (LocalTime now : new LocalTime[] {CLOSE, LocalTime.of(16, 30)}) {
            for (double strike : STRIKES) {
                for (OptionType type : OptionType.values()) {
                    double price = OPTIONS.price(SPX, strike, VIX, now, type);
                    assertThat(price).as("%s %s at %s", type, strike, now).isEqualTo(type.intrinsic(SPX, strike));
                }
            }
        }
        assertThat(OPTIONS.price(SPX, SPX, VIX, CLOSE, CALL)).as("ATM").isEqualTo(0);
        assertThat(OPTIONS.price(SPX, SPX, VIX, CLOSE, PUT)).as("ATM").isEqualTo(0);
    }

    @Test
    void oneSecondBeforeExpirationIsCloseToIntrinsic() {
        LocalTime now = LocalTime.of(15, 59, 59);
        for (double strike : STRIKES) {
            for (OptionType type : OptionType.values()) {
                double price = OPTIONS.price(SPX, strike, VIX, now, type);
                assertThat(price).isFinite().isGreaterThanOrEqualTo(type.intrinsic(SPX, strike));
            }
        }
        assertThat(OPTIONS.price(SPX, 5010, VIX, now, CALL)).as("10 points OTM").isBetween(0.0, 0.5);
        assertThat(OPTIONS.price(SPX, 4990, VIX, now, PUT)).as("10 points OTM").isBetween(0.0, 0.5);
    }

    @Test
    void timeValueShrinksToZeroAtExpiration() {
        LocalTime[] times = {LocalTime.of(15, 0), LocalTime.of(15, 30), LocalTime.of(15, 50), LocalTime.of(15, 59), CLOSE};
        Map<String, Double> strikes = Map.of("ITM", 4980.0, "ATM", 5000.0, "OTM", 5020.0);
        strikes.forEach((label, strike) -> {
            double previous = Double.POSITIVE_INFINITY;
            for (LocalTime now : times) {
                double timeValue = OPTIONS.price(SPX, strike, VIX, now, CALL) - CALL.intrinsic(SPX, strike);
                assertThat(timeValue).as("%s at %s", label, now).isGreaterThanOrEqualTo(0).isLessThanOrEqualTo(previous);
                if (label.equals("ATM") && previous != Double.POSITIVE_INFINITY) {
                    assertThat(timeValue).isLessThan(previous);
                }
                previous = timeValue;
            }
            assertThat(previous).as("%s at the close", label).isEqualTo(0);
        });
    }

    @Test
    void earlyCloseMovesExpiration() {
        TimeParams earlyClose = new TimeParams(LocalTime.of(13, 0), 252, 390);
        ZeroDteOptionPricer options = new ZeroDteOptionPricer(earlyClose, BS);

        assertThat(new ZeroDteTime(earlyClose).minutesLeft(LocalTime.NOON)).isEqualTo(60);
        assertThat(options.price(SPX, 4990, VIX, LocalTime.of(13, 0), CALL)).isEqualTo(10);
        assertThat(options.price(SPX, SPX, VIX, LocalTime.of(13, 0), PUT)).isEqualTo(0);
        assertThat(options.price(SPX, SPX, VIX, LocalTime.NOON, PUT)).isPositive();
        assertThat(new EmpiricalSpreadPricer(earlyClose, EMPIRICAL)
                .price(SPX, VIX, LocalTime.of(13, 0), BULL_PUT, 5004, 4994)).isEqualTo(4);
    }

    @Test
    void spreadsAtExpirationAreWorthIntrinsic() {
        BOTH_MODELS.forEach((model, pricer) -> {
            for (LocalTime now : new LocalTime[] {CLOSE, LocalTime.of(16, 30)}) {
                assertThat(pricer.price(SPX, VIX, now, BULL_PUT, 4990, 4980)).as("%s OTM", model).isEqualTo(0);
                assertThat(pricer.price(SPX, VIX, now, BULL_PUT, 5020, 5010)).as("%s ITM", model).isEqualTo(10);
                assertThat(pricer.price(SPX, VIX, now, BULL_PUT, 5003, 4993)).as("%s between", model).isEqualTo(3);
                assertThat(pricer.price(SPX, VIX, now, BEAR_CALL, 5010, 5020)).as("%s OTM", model).isEqualTo(0);
                assertThat(pricer.price(SPX, VIX, now, BEAR_CALL, 4980, 4990)).as("%s ITM", model).isEqualTo(10);
                assertThat(pricer.price(SPX, VIX, now, BEAR_CALL, 4997, 5007)).as("%s between", model).isEqualTo(3);
            }
        });
    }
}
