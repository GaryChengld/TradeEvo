package com.tradeevo.pricing;

import static com.tradeevo.pricing.PricingFixtures.TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class ZeroDteTimeTest {

    private final ZeroDteTime time = new ZeroDteTime(TIME);

    @Test
    void countsMinutesUntilExpirationIncludingSeconds() {
        assertThat(time.minutesLeft(LocalTime.of(16, 0))).isEqualTo(0);
        assertThat(time.minutesLeft(LocalTime.of(15, 59, 30))).isEqualTo(0.5);
        assertThat(time.minutesLeft(LocalTime.of(9, 30))).isEqualTo(390);
        assertThat(time.minutesLeft(LocalTime.of(16, 30))).as("after expiration").isEqualTo(0);
        assertThat(time.years(LocalTime.of(16, 30))).isEqualTo(0);
    }

    @Test
    void yearsUseTradingTime() {
        assertThat(time.years(LocalTime.of(9, 30))).isCloseTo(1.0 / 252, within(1e-15));
        assertThat(time.years(LocalTime.of(10, 0))).isCloseTo(360.0 / 98_280, within(1e-15));
    }

    @Test
    void honorsOtherSettings() {
        ZeroDteTime earlyClose = new ZeroDteTime(new TimeParams(LocalTime.of(13, 0), 250, 400));
        assertThat(earlyClose.minutesLeft(LocalTime.of(12, 0))).isEqualTo(60);
        assertThat(earlyClose.minutesLeft(LocalTime.of(13, 0))).isEqualTo(0);
        assertThat(earlyClose.years(LocalTime.of(12, 0))).isCloseTo(60.0 / (250 * 400), within(1e-15));
    }

    @Test
    void parameterRecordsRejectInvalidValues() {
        assertThatThrownBy(() -> new TimeParams(null, 252, 390)).hasMessageStartingWith("expiration:");
        assertThatThrownBy(() -> new TimeParams(LocalTime.NOON, 0, 390)).hasMessageStartingWith("tradingDaysPerYear:");
        assertThatThrownBy(() -> new BlackScholesParams(0, 0, 0)).hasMessageStartingWith("ivMultiplier: must be > 0");
        assertThatThrownBy(() -> new BlackScholesParams(1, Double.NaN, 0)).hasMessageStartingWith("rate:");
        assertThatThrownBy(() -> new EmpiricalParams(0.3, 3.8, 1.34, 138, 0.45, 2.5, 2.1, 0.22, 240, 55, 0.22, 150, 45))
                .hasMessageStartingWith("cMin: must be <= cMax");
        assertThatThrownBy(() -> new EmpiricalParams(0.3, 0, 1.34, 138, 0.45, 0.8, 2.1, 0.22, 240, 55, 0.22, 150, 45))
                .hasMessageStartingWith("decay: must be > 0");
    }
}
