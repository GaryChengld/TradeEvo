package com.tradeevo.strategies.example;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradeevo.core.data.DataFile;
import com.tradeevo.core.rule.Facts;
import com.tradeevo.core.rule.RuleConfig;
import com.tradeevo.core.strategy.Strategy;
import com.tradeevo.core.strategy.StrategyConfig;
import com.tradeevo.core.strategy.StrategyResult;
import com.tradeevo.core.strategy.StrategyRunContext;

/**
 * Example strategy showing how a strategy plugs into the engine. Not part of the core.
 *
 * <p>Data format (one file per date, or the {@code data} of an evaluation request):
 * <pre>{"records": [{"time": "2024-01-02T09:30:00", "value": 100.0, "x": 0.1, ...}, ...]}</pre>
 *
 * <p>Each record becomes the facts for the rules, plus {@code open} (whether a signal is open) and
 * {@code pnl} ({@code value - entryValue} while open, else 0). While flat, a matching rule with
 * action {@code OPEN} opens at the record's value; while open, a matching {@code CLOSE} rule closes.
 * Setting {@code closeAtEnd} (default true) closes an open signal at the last record.
 */
@Component
public class ThresholdSignalStrategy implements Strategy {

    public static final String OPEN = "OPEN";
    public static final String CLOSE = "CLOSE";

    @Override
    public String description() {
        return "Example: opens on a matching OPEN rule and closes on a matching CLOSE rule, tracking the change "
                + "in 'value'. Data: {\"records\": [{\"time\": ..., \"value\": <number>, <other facts>...}]}";
    }

    @Override
    public List<String> validate(StrategyConfig config) {
        List<String> errors = new ArrayList<>();
        Set<String> actions = Set.of(OPEN, CLOSE);
        boolean hasOpen = false;
        boolean hasClose = false;
        for (int i = 0; i < config.rules().size(); i++) {
            RuleConfig rule = config.rules().get(i);
            if (!actions.contains(rule.action())) {
                errors.add("rules[" + i + "].action: must be OPEN or CLOSE");
            }
            hasOpen |= OPEN.equals(rule.action());
            hasClose |= CLOSE.equals(rule.action());
        }
        if (!hasOpen) {
            errors.add("rules: at least one rule with action OPEN is required");
        }
        if (!hasClose) {
            errors.add("rules: at least one rule with action CLOSE is required");
        }
        JsonNode closeAtEnd = config.settingsOrEmpty().get("closeAtEnd");
        if (closeAtEnd != null && !closeAtEnd.isBoolean()) {
            errors.add("settings.closeAtEnd: must be a boolean");
        }
        return errors;
    }

    @Override
    public StrategyResult run(StrategyRunContext context) {
        boolean closeAtEnd = context.settings().path("closeAtEnd").asBoolean(true);
        ArrayNode signals = JsonNodeFactory.instance.arrayNode();
        List<Double> changes = new ArrayList<>();
        int records = 0;
        boolean open = false;
        double entryValue = 0;
        JsonNode last = null;

        for (DataFile file : context.data()) {
            JsonNode recordList = file.content().get("records");
            if (recordList == null || !recordList.isArray()) {
                throw new IllegalArgumentException(file.label() + ": expected an object with a 'records' array");
            }
            for (int i = 0; i < recordList.size(); i++) {
                JsonNode record = recordList.get(i);
                if (!record.isObject() || !record.path("value").isNumber()) {
                    throw new IllegalArgumentException(file.label() + ": records[" + i + "].value must be a number");
                }
                records++;
                last = record;
                double value = record.get("value").doubleValue();
                double pnl = open ? value - entryValue : 0;
                Facts facts = Facts.fromJson(record).with("open", open).with("pnl", pnl);

                if (!open && context.rules().anyMatches(OPEN, facts)) {
                    open = true;
                    entryValue = value;
                    signals.add(signal(record, OPEN, null));
                } else if (open && context.rules().anyMatches(CLOSE, facts)) {
                    open = false;
                    changes.add(pnl);
                    signals.add(signal(record, CLOSE, pnl));
                }
            }
        }
        if (open && closeAtEnd) {
            double pnl = last.get("value").doubleValue() - entryValue;
            open = false;
            changes.add(pnl);
            signals.add(signal(last, CLOSE, pnl).put("reason", "END"));
        }

        Map<String, Number> metrics = new LinkedHashMap<>();
        double total = changes.stream().mapToDouble(Double::doubleValue).sum();
        long wins = changes.stream().filter(change -> change > 0).count();
        metrics.put("records", records);
        metrics.put("signals", changes.size());
        metrics.put("totalChange", total);
        metrics.put("averageChange", changes.isEmpty() ? 0.0 : total / changes.size());
        metrics.put("winRate", changes.isEmpty() ? 0.0 : (double) wins / changes.size());

        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.set("signals", signals);
        details.put("openAtEnd", open);
        List<String> warnings = records == 0 ? List.of("no records in the data") : List.of();
        return new StrategyResult(metrics, details, warnings);
    }

    private static ObjectNode signal(JsonNode record, String action, Double change) {
        ObjectNode signal = JsonNodeFactory.instance.objectNode();
        if (record.has("time")) {
            signal.set("time", record.get("time"));
        }
        signal.put("action", action);
        signal.set("value", record.get("value"));
        if (change != null) {
            signal.put("change", change);
        }
        return signal;
    }
}
