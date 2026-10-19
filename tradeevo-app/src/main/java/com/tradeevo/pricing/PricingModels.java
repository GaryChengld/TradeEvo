package com.tradeevo.pricing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradeevo.core.error.InvalidConfigException;

/**
 * The validated contents of the pricing parameters file ({@code config/pricing-models.json}). It has no
 * defaults of its own: every parameter must be in the file, and unknown keys are errors.
 *
 * <p>A strategy's {@code pricing} block selects a model ({@code defaultModel} if omitted) and may override
 * any time parameter or parameter of that model:
 * <pre>{"model": "empirical", "overrides": {"decay": 3.5, "expiration": "13:00"}}</pre>
 */
public final class PricingModels {

    public static final String BLACK_SCHOLES = "black-scholes";
    public static final String EMPIRICAL = "empirical";

    private static final Map<String, ParamKind> TIME = schema(
            "expiration", ParamKind.TIME_OF_DAY,
            "tradingDaysPerYear", ParamKind.POSITIVE_INTEGER,
            "sessionMinutes", ParamKind.POSITIVE_INTEGER);

    private static final Map<String, Map<String, ParamKind>> MODELS = Map.of(
            BLACK_SCHOLES, schema(
                    "ivMultiplier", ParamKind.POSITIVE,
                    "rate", ParamKind.FINITE,
                    "dividend", ParamKind.FINITE),
            EMPIRICAL, schema(
                    "base", ParamKind.FINITE,
                    "decay", ParamKind.POSITIVE,
                    "cScale", ParamKind.FINITE,
                    "cPivot", ParamKind.POSITIVE,
                    "cExponent", ParamKind.FINITE,
                    "cMin", ParamKind.FINITE,
                    "cMax", ParamKind.FINITE,
                    "mHeight", ParamKind.FINITE,
                    "mCenter", ParamKind.FINITE,
                    "mWidth", ParamKind.POSITIVE,
                    "aHeight", ParamKind.FINITE,
                    "aCenter", ParamKind.FINITE,
                    "aWidth", ParamKind.POSITIVE));

    private static final List<String> MODEL_NAMES = List.of(BLACK_SCHOLES, EMPIRICAL);

    private final String defaultModel;
    private final ObjectNode time;
    private final Map<String, ObjectNode> models;

    private PricingModels(String defaultModel, ObjectNode time, Map<String, ObjectNode> models) {
        this.defaultModel = defaultModel;
        this.time = time;
        this.models = models;
    }

    /** Reads and validates the file. @throws InvalidConfigException if it is missing, unreadable or invalid */
    public static PricingModels load(Path file, ObjectMapper mapper) {
        String summary = "Invalid pricing file " + file;
        if (!Files.isRegularFile(file)) {
            throw new InvalidConfigException(summary,
                    List.of(file + ": file not found (set tradeevo.pricing-file to its location)"));
        }
        JsonNode root;
        try {
            root = mapper.readTree(file.toFile());
        } catch (IOException e) {
            String reason = e instanceof JsonProcessingException json ? json.getOriginalMessage() : e.toString();
            throw new InvalidConfigException(summary, List.of(file + ": " + reason));
        }
        return parse(root, summary);
    }

    /** Validates parsed file contents. @throws InvalidConfigException listing every error with its path */
    public static PricingModels parse(JsonNode root) {
        return parse(root, "Invalid pricing parameters");
    }

    private static PricingModels parse(JsonNode root, String summary) {
        List<String> errors = new ArrayList<>();
        if (root == null || !root.isObject()) {
            throw new InvalidConfigException(summary, List.of("(root): must be an object"));
        }
        unknownKeys(root, Set.of("defaultModel", "time", "models"), "", "unknown property", errors);

        ObjectNode time = section(root, "time", "time", errors);
        if (time != null) {
            checkSection(time, TIME, "time.", errors);
        }

        Map<String, ObjectNode> models = new LinkedHashMap<>();
        ObjectNode modelsNode = section(root, "models", "models", errors);
        if (modelsNode != null) {
            unknownKeys(modelsNode, MODELS.keySet(), "models.", "unknown model, must be one of " + MODEL_NAMES,
                    errors);
            for (String name : MODEL_NAMES) {
                ObjectNode model = section(modelsNode, name, "models." + name, errors);
                if (model != null) {
                    checkSection(model, MODELS.get(name), "models." + name + ".", errors);
                    models.put(name, model);
                }
            }
        }

        JsonNode defaultModel = root.get("defaultModel");
        if (defaultModel == null) {
            errors.add("defaultModel: is required");
        } else if (!defaultModel.isTextual() || !MODELS.containsKey(defaultModel.asText())) {
            errors.add("defaultModel: must be one of " + MODEL_NAMES);
        }

        if (!errors.isEmpty()) {
            throw new InvalidConfigException(summary, errors);
        }
        return new PricingModels(defaultModel.asText(), time, models);
    }

    public String defaultModel() {
        return defaultModel;
    }

    /** Time parameters from the file, without overrides. */
    public TimeParams timeParams() {
        return timeParams(time);
    }

    /** Black-Scholes parameters from the file, without overrides. */
    public BlackScholesParams blackScholesParams() {
        return blackScholesParams(models.get(BLACK_SCHOLES));
    }

    /** Empirical parameters from the file, without overrides. */
    public EmpiricalParams empiricalParams() {
        return empiricalParams(models.get(EMPIRICAL));
    }

    /**
     * Validates a strategy's {@code pricing} block. Null or missing is valid and selects the default model.
     *
     * @param path JSON path of the block, prefixed to each error (e.g. {@code settings.pricing})
     */
    public List<String> validate(JsonNode pricing, String path) {
        List<String> errors = new ArrayList<>();
        if (pricing == null || pricing.isNull() || pricing.isMissingNode()) {
            return errors;
        }
        if (!pricing.isObject()) {
            errors.add(path + ": must be an object");
            return errors;
        }
        unknownKeys(pricing, Set.of("model", "overrides"), path + ".", "unknown property", errors);
        String model = modelName(pricing);
        if (model == null) {
            errors.add(path + ".model: must be one of " + MODEL_NAMES);
            return errors;
        }
        JsonNode overrides = pricing.get("overrides");
        if (overrides == null || overrides.isNull()) {
            return errors;
        }
        if (!overrides.isObject()) {
            errors.add(path + ".overrides: must be an object");
            return errors;
        }
        Map<String, ParamKind> modelSchema = MODELS.get(model);
        overrides.fieldNames().forEachRemaining(key -> {
            ParamKind kind = TIME.containsKey(key) ? TIME.get(key) : modelSchema.get(key);
            if (kind == null) {
                errors.add(path + ".overrides." + key + ": unknown parameter for model '" + model + "'");
                return;
            }
            String problem = kind.problem(overrides.get(key));
            if (problem != null) {
                errors.add(path + ".overrides." + key + ": " + problem);
            }
        });
        if (errors.isEmpty() && EMPIRICAL.equals(model)) {
            ObjectNode merged = merge(models.get(EMPIRICAL), overrides, modelSchema.keySet());
            if (merged.get("cMin").doubleValue() > merged.get("cMax").doubleValue()) {
                String key = overrides.has("cMin") ? "cMin" : "cMax";
                errors.add(path + ".overrides." + key + ": cMin must be <= cMax, was " + merged.get("cMin")
                        + " > " + merged.get("cMax"));
            }
        }
        return errors;
    }

    /**
     * Builds the pricer selected by a strategy's {@code pricing} block, with overrides applied.
     *
     * @throws InvalidConfigException listing every error of {@link #validate}
     */
    public ResolvedPricer resolve(JsonNode pricing, String path) {
        List<String> errors = validate(pricing, path);
        if (!errors.isEmpty()) {
            throw new InvalidConfigException("Invalid " + path, errors);
        }
        boolean present = pricing != null && pricing.isObject();
        String model = present ? modelName(pricing) : defaultModel;
        JsonNode overrides = present ? pricing.get("overrides") : null;
        ObjectNode resolvedTime = merge(time, overrides, TIME.keySet());
        ObjectNode resolvedParams = merge(models.get(model), overrides, MODELS.get(model).keySet());

        TimeParams timeParams = timeParams(resolvedTime);
        SpreadPricer pricer = BLACK_SCHOLES.equals(model)
                ? new BlackScholesSpreadPricer(timeParams, blackScholesParams(resolvedParams))
                : new EmpiricalSpreadPricer(timeParams, empiricalParams(resolvedParams));
        ObjectNode parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("model", model);
        parameters.set("time", resolvedTime);
        parameters.set("params", resolvedParams);
        return new ResolvedPricer(model, pricer, parameters);
    }

    /** The selected model name, the default if absent, or null if it is not a known model. */
    private String modelName(JsonNode pricing) {
        JsonNode model = pricing.get("model");
        if (model == null || model.isNull()) {
            return defaultModel;
        }
        return model.isTextual() && MODELS.containsKey(model.asText()) ? model.asText() : null;
    }

    /** The schema's keys from {@code base}, replaced by any in {@code overrides}, in schema order. */
    private static ObjectNode merge(ObjectNode base, JsonNode overrides, Set<String> keys) {
        ObjectNode merged = JsonNodeFactory.instance.objectNode();
        for (String key : keys) {
            JsonNode value = overrides != null && overrides.has(key) ? overrides.get(key) : base.get(key);
            merged.set(key, value.deepCopy());
        }
        return merged;
    }

    private static TimeParams timeParams(ObjectNode node) {
        return new TimeParams(LocalTime.parse(node.get("expiration").asText()),
                node.get("tradingDaysPerYear").intValue(), node.get("sessionMinutes").intValue());
    }

    private static BlackScholesParams blackScholesParams(ObjectNode node) {
        return new BlackScholesParams(d(node, "ivMultiplier"), d(node, "rate"), d(node, "dividend"));
    }

    private static EmpiricalParams empiricalParams(ObjectNode node) {
        return new EmpiricalParams(d(node, "base"), d(node, "decay"),
                d(node, "cScale"), d(node, "cPivot"), d(node, "cExponent"), d(node, "cMin"), d(node, "cMax"),
                d(node, "mHeight"), d(node, "mCenter"), d(node, "mWidth"),
                d(node, "aHeight"), d(node, "aCenter"), d(node, "aWidth"));
    }

    private static double d(ObjectNode node, String key) {
        return node.get(key).doubleValue();
    }

    private static ObjectNode section(JsonNode parent, String key, String path, List<String> errors) {
        JsonNode node = parent.get(key);
        if (node == null) {
            errors.add(path + ": is required");
            return null;
        }
        if (!node.isObject()) {
            errors.add(path + ": must be an object");
            return null;
        }
        return (ObjectNode) node;
    }

    private static void checkSection(ObjectNode node, Map<String, ParamKind> schema, String prefix,
            List<String> errors) {
        unknownKeys(node, schema.keySet(), prefix, "unknown parameter", errors);
        schema.forEach((key, kind) -> {
            JsonNode value = node.get(key);
            if (value == null) {
                errors.add(prefix + key + ": is required");
                return;
            }
            String problem = kind.problem(value);
            if (problem != null) {
                errors.add(prefix + key + ": " + problem);
            }
        });
        JsonNode cMin = node.get("cMin");
        JsonNode cMax = node.get("cMax");
        if (cMin != null && cMax != null && cMin.isNumber() && cMax.isNumber()
                && cMin.doubleValue() > cMax.doubleValue()) {
            errors.add(prefix + "cMin: must be <= cMax, was " + cMin + " > " + cMax);
        }
    }

    private static void unknownKeys(JsonNode node, Set<String> allowed, String prefix, String message,
            List<String> errors) {
        for (Iterator<String> it = node.fieldNames(); it.hasNext();) {
            String key = it.next();
            if (!allowed.contains(key)) {
                errors.add(prefix + key + ": " + message);
            }
        }
    }

    private static Map<String, ParamKind> schema(Object... nameKindPairs) {
        Map<String, ParamKind> schema = new LinkedHashMap<>();
        for (int i = 0; i < nameKindPairs.length; i += 2) {
            schema.put((String) nameKindPairs[i], (ParamKind) nameKindPairs[i + 1]);
        }
        return schema;
    }
}
