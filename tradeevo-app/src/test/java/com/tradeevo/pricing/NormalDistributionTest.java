package com.tradeevo.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class NormalDistributionTest {

    @Test
    void matchesReferenceValues() {
        assertThat(NormalDistribution.cdf(0)).isEqualTo(0.5);
        assertThat(NormalDistribution.cdf(1)).isCloseTo(0.8413447460685429, within(1e-14));
        assertThat(NormalDistribution.cdf(-1)).isCloseTo(0.15865525393145707, within(1e-14));
        assertThat(NormalDistribution.cdf(1.96)).isCloseTo(0.9750021048517795, within(1e-14));
        assertThat(NormalDistribution.cdf(-1.96)).isCloseTo(0.024997895148220435, within(1e-14));
        assertThat(NormalDistribution.cdf(5)).isCloseTo(0.9999997133484281, within(1e-14));
        // Hart's algorithm is accurate to ~1e-15 absolute; in the far tail that is ~1e-11 relative.
        double tail = NormalDistribution.cdf(-5);
        assertThat(tail).isCloseTo(2.866515718791939e-7, within(1e-15));
        assertThat(Math.abs(tail - 2.866515718791939e-7) / 2.866515718791939e-7).isLessThan(1e-9);
    }

    @Test
    void isSymmetric() {
        for (double x : new double[] {0.1, 0.5, 1, 2.5, 6, 7.5, 10}) {
            assertThat(NormalDistribution.cdf(x) + NormalDistribution.cdf(-x)).as("x=%s", x).isCloseTo(1, within(1e-15));
        }
    }

    @Test
    void extremeArgumentsStayFinite() {
        assertThat(NormalDistribution.cdf(40)).isEqualTo(1);
        assertThat(NormalDistribution.cdf(-40)).isEqualTo(0);
        assertThat(NormalDistribution.cdf(1e300)).isEqualTo(1);
        assertThat(NormalDistribution.cdf(-1e300)).isEqualTo(0);
        assertThat(NormalDistribution.cdf(Double.POSITIVE_INFINITY)).isEqualTo(1);
        assertThat(NormalDistribution.cdf(Double.NEGATIVE_INFINITY)).isEqualTo(0);
    }
}
