package com.tradeevo.service;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Run a stored strategy version (latest when {@code version} is null) over data files in a date range. */
public record BacktestRequest(
        @NotBlank String strategyId,
        @Positive Integer version,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
}
