package com.tradeevo.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Evaluate a strategy right away on data supplied in the request.
 *
 * @param strategy {@code {"strategyId": ..., "version": ...}} or an inline config with {@code strategyClass}
 * @param data     raw data in the strategy's own format (the same shape as one data file)
 */
public record EvaluationRequest(JsonNode strategy, JsonNode data) {
}
