package com.tradeevo.pricing;

import static com.tradeevo.pricing.OptionType.CALL;
import static com.tradeevo.pricing.OptionType.PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class BlackScholesTest {

    @Test
    void matchesTextbookExample() {
        // Hull, Options, Futures, and Other Derivatives: S=42, K=40, r=10%, sigma=20%, T=0.5
        assertThat(BlackScholes.price(42, 40, 0.2, 0.5, 0.1, 0, CALL)).isCloseTo(4.76, within(0.01));
        assertThat(BlackScholes.price(42, 40, 0.2, 0.5, 0.1, 0, PUT)).isCloseTo(0.81, within(0.01));
    }

    @Test
    void putCallParityHolds() {
        double[][] cases = {{5000, 5000, 0.15, 0.003, 0, 0}, {5000, 4900, 0.3, 0.01, 0.05, 0.015},
                {42, 40, 0.2, 0.5, 0.1, 0.02}, {100, 130, 0.6, 2, 0.03, 0}};
        for (double[] c : cases) {
            double call = BlackScholes.price(c[0], c[1], c[2], c[3], c[4], c[5], CALL);
            double put = BlackScholes.price(c[0], c[1], c[2], c[3], c[4], c[5], PUT);
            double parity = c[0] * Math.exp(-c[5] * c[3]) - c[1] * Math.exp(-c[4] * c[3]);
            assertThat(call - put).as("case %s", java.util.Arrays.toString(c)).isCloseTo(parity, within(1e-10));
        }
    }

    @Test
    void atOrAfterExpiryAndWithZeroVolatilityThePriceIsIntrinsic() {
        for (double years : new double[] {0, -0.01}) {
            assertThat(BlackScholes.price(5000, 4990, 0.15, years, 0, 0, CALL)).isEqualTo(10);
            assertThat(BlackScholes.price(5000, 4990, 0.15, years, 0, 0, PUT)).isEqualTo(0);
            assertThat(BlackScholes.price(5000, 5010, 0.15, years, 0, 0, PUT)).isEqualTo(10);
        }
        assertThat(BlackScholes.price(5000, 4990, 0, 0.01, 0, 0, CALL)).isEqualTo(10);
        assertThat(BlackScholes.price(5000, 5010, 0, 0.01, 0, 0, CALL)).isEqualTo(0);
    }

    @Test
    void priceRisesWithVolatilityAndTime() {
        double previous = 0;
        for (double sigma : new double[] {0.05, 0.1, 0.2, 0.4}) {
            double price = BlackScholes.price(5000, 5000, sigma, 0.003, 0, 0, CALL);
            assertThat(price).isGreaterThan(previous);
            previous = price;
        }
        previous = 0;
        for (double years : new double[] {0.0001, 0.001, 0.003, 0.01}) {
            double price = BlackScholes.price(5000, 5000, 0.15, years, 0, 0, PUT);
            assertThat(price).isGreaterThan(previous);
            previous = price;
        }
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> BlackScholes.price(0, 40, 0.2, 0.5, 0, 0, CALL)).hasMessageStartingWith("spot:");
        assertThatThrownBy(() -> BlackScholes.price(42, Double.NaN, 0.2, 0.5, 0, 0, CALL)).hasMessageStartingWith("strike:");
        assertThatThrownBy(() -> BlackScholes.price(42, 40, -0.1, 0.5, 0, 0, CALL)).hasMessageStartingWith("sigma:");
        assertThatThrownBy(() -> BlackScholes.price(42, 40, 0.2, Double.POSITIVE_INFINITY, 0, 0, CALL))
                .hasMessageStartingWith("years:");
    }
}
