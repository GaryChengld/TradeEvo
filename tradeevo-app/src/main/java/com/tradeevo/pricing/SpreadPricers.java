package com.tradeevo.pricing;

import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeevo.core.error.InvalidConfigException;

/**
 * Entry point for strategies: builds spread pricers from the pricing parameters file and the strategy's
 * {@code settings.pricing} block.
 *
 * <p>The file is read on first use and the result (or the failure) is kept for the life of the process, so a
 * strategy that never prices options never needs it, and an edit takes effect on the next CLI run or server
 * restart.
 */
public class SpreadPricers {

    /** JSON path of the pricing block in a strategy config, used in error messages. */
    public static final String SETTINGS_PATH = "settings.pricing";

    private final Path file;
    private final ObjectMapper mapper;
    private PricingModels models;
    private InvalidConfigException failure;

    public SpreadPricers(Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
    }

    /** The file's contents, loaded once. @throws InvalidConfigException if the file is missing or invalid */
    public synchronized PricingModels models() {
        if (models == null && failure == null) {
            try {
                models = PricingModels.load(file, mapper);
            } catch (InvalidConfigException e) {
                failure = e;
            }
        }
        if (failure != null) {
            throw new InvalidConfigException("Invalid pricing file " + file, failure.getErrors());
        }
        return models;
    }

    /** Errors in a strategy's pricing block, for {@code Strategy.validate}. */
    public List<String> validate(JsonNode pricing, String path) {
        return models().validate(pricing, path);
    }

    /** The pricer for a strategy's {@code settings.pricing} block (null for the default model). */
    public ResolvedPricer create(JsonNode pricing) {
        return models().resolve(pricing, SETTINGS_PATH);
    }
}
