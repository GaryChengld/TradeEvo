package com.tradeevo.pricing;

import static com.tradeevo.pricing.OptionType.CALL;
import static com.tradeevo.pricing.OptionType.PUT;
import static com.tradeevo.pricing.PricingFixtures.CLOSE;
import static com.tradeevo.pricing.PricingFixtures.OPTIONS;
import static com.tradeevo.pricing.PricingFixtures.SPX;
import static com.tradeevo.pricing.PricingFixtures.TEN;
import static com.tradeevo.pricing.PricingFixtures.TIME;
import static com.tradeevo.pricing.PricingFixtures.VIX;
import static com.tradeevo.pricing.PricingFixtures.assertWithinTolerance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class ZeroDteOptionPricerTest {

    @Test
    void atmStraddleAtTheOpenMatchesTheMarketRuleOfThumb() {
        double straddle = OPTIONS.price(SPX, SPX, VIX, TEN, CALL) + OPTIONS.price(SPX, SPX, VIX, TEN, PUT);

        assertWithinTolerance(straddle, 36);
        assertThat(straddle / SPX).as("market rule of thumb: 0.7–1% of SPX").isBetween(0.007, 0.01);
    }

    @Test
    void fromExpirationOnwardsThePriceIsIntrinsic() {
        for (LocalTime now : new LocalTime[] {CLOSE, LocalTime.of(16, 30)}) {
            assertThat(OPTIONS.price(SPX, 4990, VIX, now, CALL)).isEqualTo(10);
            assertThat(OPTIONS.price(SPX, 4990, VIX, now, PUT)).isEqualTo(0);
            assertThat(OPTIONS.price(SPX, SPX, VIX, now, CALL)).isEqualTo(0);
        }
    }

    @Test
    void ivMultiplierScalesVix() {
        ZeroDteOptionPricer unit = new ZeroDteOptionPricer(TIME, new BlackScholesParams(1.0, 0, 0));
        double sigma = VIX / 100;
        double years = new ZeroDteTime(TIME).years(TEN);
        assertThat(unit.price(SPX, 5010, VIX, TEN, CALL))
                .isEqualTo(BlackScholes.price(SPX, 5010, sigma, years, 0, 0, CALL));

        double previous = 0;
        for (double k : new double[] {0.5, 1.0, 1.15, 2.0}) {
            ZeroDteOptionPricer scaled = new ZeroDteOptionPricer(TIME, new BlackScholesParams(k, 0, 0));
            double price = scaled.price(SPX, 5010, VIX, TEN, CALL);
            assertThat(price).as("k=%s", k).isCloseTo(unit.price(SPX, 5010, VIX * k, TEN, CALL), within(1e-9));
            assertThat(price).isGreaterThan(previous);
            previous = price;
        }
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> OPTIONS.price(Double.NaN, SPX, VIX, TEN, CALL)).hasMessageStartingWith("spx:");
        assertThatThrownBy(() -> OPTIONS.price(SPX, -1, VIX, TEN, CALL)).hasMessageStartingWith("strike:");
        assertThatThrownBy(() -> OPTIONS.price(SPX, SPX, 0, TEN, CALL)).hasMessageStartingWith("vix:");
        assertThatThrownBy(() -> OPTIONS.price(SPX, SPX, VIX, null, CALL)).hasMessageStartingWith("now:");
        assertThatThrownBy(() -> OPTIONS.price(SPX, SPX, VIX, TEN, null)).hasMessageStartingWith("type:");
    }
}
