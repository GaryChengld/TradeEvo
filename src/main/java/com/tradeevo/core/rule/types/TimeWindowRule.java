package com.tradeevo.core.rule.types;

import static com.tradeevo.core.rule.ParamSpec.required;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.rule.ParamSchema;
import com.tradeevo.core.rule.ParamType;
import com.tradeevo.core.rule.Rule;
import com.tradeevo.core.rule.RuleFactory;
import com.tradeevo.core.rule.RuleType;
import com.tradeevo.core.rule.Values;

/** The time of day of {@code field} is in {@code [start, end)}; wraps past midnight when start > end. */
@Component
public class TimeWindowRule implements RuleType {

    private static final ParamSchema SCHEMA = ParamSchema.of(
            required("field", ParamType.STRING, "Fact holding a time or date-time (ISO-8601)"),
            required("start", ParamType.STRING, "Window start, inclusive (HH:mm or HH:mm:ss)"),
            required("end", ParamType.STRING, "Window end, exclusive (HH:mm or HH:mm:ss)"));

    @Override
    public String name() {
        return "TimeWindow";
    }

    @Override
    public String description() {
        return "Matches when the time of day of a fact is in [start, end). The fact may be a time, "
                + "a local date-time or an offset date-time (ISO-8601). Windows with start > end wrap past midnight.";
    }

    @Override
    public ParamSchema schema() {
        return SCHEMA;
    }

    @Override
    public List<String> validateParams(JsonNode params, String path) {
        List<String> errors = new ArrayList<>();
        LocalTime start = parse(params.get("start").textValue(), path + ".start", errors);
        LocalTime end = parse(params.get("end").textValue(), path + ".end", errors);
        if (start != null && start.equals(end)) {
            errors.add(path + ": 'start' and 'end' must differ");
        }
        return errors;
    }

    private static LocalTime parse(String text, String path, List<String> errors) {
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException e) {
            errors.add(path + ": must be a time like 09:30 or 09:30:00");
            return null;
        }
    }

    @Override
    public Rule create(JsonNode params, RuleFactory factory) {
        String field = params.get("field").textValue();
        LocalTime start = LocalTime.parse(params.get("start").textValue());
        LocalTime end = LocalTime.parse(params.get("end").textValue());
        boolean wraps = start.isAfter(end);
        return facts -> {
            LocalTime time = Values.toLocalTime(facts.get(field));
            if (time == null) {
                return false;
            }
            boolean afterStart = !time.isBefore(start);
            boolean beforeEnd = time.isBefore(end);
            return wraps ? afterStart || beforeEnd : afterStart && beforeEnd;
        };
    }
}
