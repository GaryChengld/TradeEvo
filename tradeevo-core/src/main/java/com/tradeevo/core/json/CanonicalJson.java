package com.tradeevo.core.json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Canonical JSON (object keys sorted recursively, compact output) for content hashing, plus
 * defensive copies of mutable {@link JsonNode}s.
 */
public final class CanonicalJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CanonicalJson() {
    }

    /** A deep copy, or null for null. Use at API boundaries: Jackson object and array nodes are mutable. */
    public static JsonNode copy(JsonNode node) {
        return node == null ? null : node.deepCopy();
    }

    /** A copy of the node with every object's keys in sorted order. */
    public static JsonNode canonical(JsonNode node) {
        if (node == null) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isObject()) {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.properties().forEach(entry -> sorted.put(entry.getKey(), canonical(entry.getValue())));
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            sorted.forEach(result::set);
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode(node.size());
            node.forEach(element -> result.add(canonical(element)));
            return result;
        }
        return node;
    }

    public static String toCanonicalString(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(canonical(node));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize JSON", e);
        }
    }

    /** SHA-256 (hex) of the canonical form: equal content gives an equal hash regardless of key order. */
    public static String sha256(JsonNode node) {
        return sha256(toCanonicalString(node));
    }

    public static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Converts a value (e.g. a record) to a JSON tree. */
    public static JsonNode toTree(Object value) {
        return MAPPER.valueToTree(value);
    }
}
