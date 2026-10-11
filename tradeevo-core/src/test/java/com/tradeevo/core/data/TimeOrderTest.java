package com.tradeevo.core.data;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class TimeOrderTest {

    @Test
    void acceptsStrictlyIncreasingTimesAcrossFiles() {
        TimeOrder order = new TimeOrder();
        assertThatCode(() -> {
            order.accept("2024-01-02T09:30:00", "2024-01-02 records[0]");
            order.accept("2024-01-02T15:59:59", "2024-01-02 records[1]");
            order.accept(LocalDateTime.of(2024, 1, 3, 9, 30), "2024-01-03 records[0]");
        }).doesNotThrowAnyException();

        TimeOrder offsets = new TimeOrder();
        assertThatCode(() -> {
            offsets.accept("2024-01-02T09:30:00-05:00", "a");
            offsets.accept("2024-01-02T14:31:00Z", "b"); // 09:31 in New York
            offsets.accept(Instant.parse("2024-01-02T14:32:00Z"), "c");
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsDecreasingTimesWithBothLocations() {
        TimeOrder order = new TimeOrder();
        order.accept("2024-01-03T09:30:00", "2024-01-03 records[0]");

        assertThatThrownBy(() -> order.accept("2024-01-03T09:00:00", "2024-01-03 records[1]"))
                .isInstanceOf(InvalidRecordOrderException.class)
                .hasMessage("2024-01-03 records[1]: time 2024-01-03T09:00:00 is not after 2024-01-03 records[0] "
                        + "(2024-01-03T09:30:00)");
    }

    @Test
    void rejectsOrderErrorsAcrossFileBoundaries() {
        TimeOrder order = new TimeOrder();
        order.accept("2024-01-03T09:30:00", "2024-01-02 records[4]");

        assertThatThrownBy(() -> order.accept("2024-01-03T09:30:00", "2024-01-03 records[0]"))
                .hasMessage("2024-01-03 records[0]: duplicate timestamp 2024-01-03T09:30:00 (same as 2024-01-02 records[4])");
    }

    @Test
    void offsetTimesAreComparedAsInstants() {
        TimeOrder order = new TimeOrder();
        order.accept("2024-01-02T10:00:00-05:00", "a"); // 15:00Z

        assertThatThrownBy(() -> order.accept("2024-01-02T14:00:00Z", "b")).hasMessageContaining("is not after a");
    }

    @Test
    void rejectsMissingUnparseableAndMixedTimes() {
        assertThatThrownBy(() -> new TimeOrder().accept(null, "r0")).hasMessage("r0: time is missing");
        assertThatThrownBy(() -> new TimeOrder().accept("09:30", "r0"))
                .hasMessage("r0: time '09:30' is not an ISO-8601 date-time");

        TimeOrder order = new TimeOrder();
        order.accept("2024-01-02T09:30:00", "r0");
        assertThatThrownBy(() -> order.accept("2024-01-02T09:31:00Z", "r1")).hasMessageContaining("mixes local and offset times");
    }
}
