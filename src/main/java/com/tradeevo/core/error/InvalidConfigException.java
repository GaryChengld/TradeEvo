package com.tradeevo.core.error;

import java.util.List;

/** A strategy config failed validation. Carries every error found, each prefixed with its JSON path. */
public class InvalidConfigException extends BadRequestException {

    private final List<String> errors;

    public InvalidConfigException(List<String> errors) {
        super("Invalid strategy config: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
