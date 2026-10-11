package com.tradeevo.store;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A strategy and its versions; description and class are taken from the latest version. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StrategySummary(String id, int latestVersion, List<Integer> versions, String description, String strategyClass) {
}
