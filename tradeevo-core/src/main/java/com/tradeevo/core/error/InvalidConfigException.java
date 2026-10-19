package com.tradeevo.core.error;

import java.util.List;

/** A strategy config failed validation. Carries every error found, each prefixed with its JSON path. */
public class InvalidConfigException extends BadRequestException {

    private final List<String> errors;

    public InvalidConfigException(List<String> errors) {
        this("Invalid strategy config", errors);
    }

    /** @param summary what was invalid, e.g. "Invalid pricing file ./config/pricing-models.json" */
    public InvalidConfigException(String summary, List<String> errors) {
        super(summary + ": " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
