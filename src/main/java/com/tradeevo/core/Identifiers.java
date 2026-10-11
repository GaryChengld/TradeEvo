package com.tradeevo.core;

import java.util.regex.Pattern;

/** Identifiers (strategy ids, datasets, run ids) double as directory names, so they are restricted. */
public final class Identifiers {

    public static final String RULE = "1-100 characters: letters, digits, '.', '_' or '-', starting with a letter or digit";

    private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");

    private Identifiers() {
    }

    public static boolean isValid(String value) {
        return value != null && PATTERN.matcher(value).matches() && !value.contains("..");
    }
}
