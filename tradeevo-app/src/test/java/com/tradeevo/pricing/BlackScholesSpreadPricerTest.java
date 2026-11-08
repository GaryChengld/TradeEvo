package com.tradeevo.pricing;

import static com.tradeevo.pricing.PricingFixtures.BS_SPREADS;
import static com.tradeevo.pricing.PricingFixtures.CLOSE;
import static com.tradeevo.pricing.PricingFixtures.SPX;
import static com.tradeevo.pricing.PricingFixtures.TEN;
import static com.tradeevo.pricing.PricingFixtures.VIX;
import static com.tradeevo.pricing.PricingFixtures.assertWithinTolerance;
import static com.tradeevo.pricing.SpreadDirection.BEAR_CALL;
import static com.tradeevo.pricing.SpreadDirection.BULL_PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BlackScholesSpreadPricerTest {

    @Test
    void narrowSpreadAtTheMoneyIsWorthAboutHalfItsWidth() {
        assertWithinTolerance(BS_SPREADS.price(SPX, VIX, TEN, BULL_PUT, 5000, 4995), 2.5);
        assertWithinTolerance(BS_SPREADS.price(SPX, VIX, TEN, BEAR_CALL, 5000, 5005), 2.5);
    }

    @Test
    void isShortLegMinusLongLeg() {
        double shortPut = PricingFixtures.OPTIONS.price(SPX, 4980, VIX, TEN, OptionType.PUT);
        double longPut = PricingFixtures.OPTIONS.price(SPX, 4970, VIX, TEN, OptionType.PUT);
        assertThat(BS_SPREADS.price(SPX, VIX, TEN, BULL_PUT, 4980, 4970)).isEqualTo(shortPut - longPut);
    }

    @Test
    void withSkewEachLegUsesItsOwnVolatility() {
        BlackScholesParams skewed = new BlackScholesParams(0.59, 0.21, 0, 0);
        double shortPut = new ZeroDteOptionPricer(PricingFixtures.TIME, skewed).price(SPX, 4950, VIX, TEN, OptionType.PUT);
        double longPut = new ZeroDteOptionPricer(PricingFixtures.TIME, skewed).price(SPX, 4900, VIX, TEN, OptionType.PUT);
        double price = new BlackScholesSpreadPricer(PricingFixtures.TIME, skewed).price(SPX, VIX, TEN, BULL_PUT, 4950, 4900);
        assertThat(price).isEqualTo(shortPut - longPut);

        // The long leg is 2% out, so it gets more volatility than a flat 1%-skew model would give it.
        BlackScholesParams flatAtShortLeg = new BlackScholesParams(0.59 * 1.21, 0, 0, 0);
        assertThat(price).isLessThan(
                new BlackScholesSpreadPricer(PricingFixtures.TIME, flatAtShortLeg).price(SPX, VIX, TEN, BULL_PUT, 4950, 4900));
    }

    @Test
    void atExpiryReturnsIntrinsic() {
        assertThat(BS_SPREADS.price(SPX, VIX, CLOSE, BULL_PUT, 4990, 4980)).isEqualTo(0);
        assertThat(BS_SPREADS.price(SPX, VIX, CLOSE, BULL_PUT, 5004, 4994)).isEqualTo(4);
        assertThat(BS_SPREADS.price(SPX, VIX, CLOSE, BEAR_CALL, 4980, 4990)).isEqualTo(10);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> BS_SPREADS.price(SPX, VIX, TEN, BULL_PUT, 4990, 5000))
                .hasMessageStartingWith("longStrike: must be below shortStrike for BULL_PUT");
        assertThatThrownBy(() -> BS_SPREADS.price(SPX, VIX, TEN, BEAR_CALL, 5010, 5010))
                .hasMessageStartingWith("longStrike: must be above shortStrike for BEAR_CALL");
        assertThatThrownBy(() -> BS_SPREADS.price(SPX, Double.NaN, TEN, BULL_PUT, 4990, 4980)).hasMessageStartingWith("vix:");
        assertThatThrownBy(() -> BS_SPREADS.price(-5, VIX, TEN, BULL_PUT, 4990, 4980)).hasMessageStartingWith("spx:");
        assertThatThrownBy(() -> BS_SPREADS.price(SPX, VIX, TEN, null, 4990, 4980)).hasMessageStartingWith("direction:");
        assertThatThrownBy(() -> PricingFixtures.EMPIRICAL_SPREADS.price(SPX, VIX, TEN, BULL_PUT, 4990, 0))
                .hasMessageStartingWith("longStrike: must be a finite number > 0");
    }
}
