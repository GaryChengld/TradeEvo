package com.tradeevo.core.engine;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradeevo.core.Identifiers;
import com.tradeevo.core.data.DataFile;
import com.tradeevo.core.error.InvalidConfigException;
import com.tradeevo.core.rule.RuleFactory;
import com.tradeevo.core.rule.RuleSet;
import com.tradeevo.core.strategy.RunMode;
import com.tradeevo.core.strategy.Strategy;
import com.tradeevo.core.strategy.StrategyClassRegistry;
import com.tradeevo.core.strategy.StrategyConfig;
import com.tradeevo.core.strategy.StrategyResult;
import com.tradeevo.core.strategy.StrategyRunContext;

/** Validates a strategy config, builds its rules, and triggers its strategy class on raw data. */
@Component
public class StrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(StrategyEngine.class);
    private static final DateTimeFormatter RUN_ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final StrategyClassRegistry strategies;
    private final RuleFactory ruleFactory;
    private final Clock clock;

    public StrategyEngine(StrategyClassRegistry strategies, RuleFactory ruleFactory, Clock clock) {
        this.strategies = strategies;
        this.ruleFactory = ruleFactory;
        this.clock = clock;
    }

    /** Returns every problem with the config; empty when it can run. */
    public List<String> validate(StrategyConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) {
            errors.add("strategy config is required");
            return errors;
        }
        if (config.id() == null || config.id().isBlank()) {
            errors.add("id: required");
        } else if (!Identifiers.isValid(config.id())) {
            errors.add("id: " + Identifiers.RULE);
        }
        if (config.dataset() != null && !Identifiers.isValid(config.dataset())) {
            errors.add("dataset: " + Identifiers.RULE);
        }
        if (config.settings() != null && !config.settings().isNull() && !config.settings().isObject()) {
            errors.add("settings: must be an object");
        }
        Strategy strategy = null;
        if (config.strategyClass() == null || config.strategyClass().isBlank()) {
            errors.add("strategyClass: required");
        } else {
            strategy = strategies.find(config.strategyClass()).orElse(null);
            if (strategy == null) {
                errors.add("strategyClass: unknown strategy class '" + config.strategyClass() + "' (available: "
                        + strategies.all().stream().map(StrategyClassRegistry.StrategyClassInfo::className).toList() + ")");
            }
        }
        List<String> ruleErrors = ruleFactory.validateRuleConfigs(config.rules());
        errors.addAll(ruleErrors);
        if (strategy != null && ruleErrors.isEmpty()) {
            errors.addAll(strategy.validate(config));
        }
        return errors;
    }

    /** @throws InvalidConfigException listing every problem, if the config cannot run */
    public void requireValid(StrategyConfig config) {
        List<String> errors = validate(config);
        if (!errors.isEmpty()) {
            throw new InvalidConfigException(errors);
        }
    }

    /**
     * Runs the strategy. An invalid config throws {@link InvalidConfigException}; an exception
     * thrown by the strategy itself produces a {@link RunStatus#FAILED} record.
     *
     * @param startDate the requested range start, recorded as-is (null for evaluations)
     * @param endDate   the requested range end, recorded as-is (null for evaluations)
     */
    public RunRecord run(StrategyConfig config, List<DataFile> data, RunMode mode, LocalDate startDate, LocalDate endDate) {
        requireValid(config);
        Strategy strategy = strategies.find(config.strategyClass()).orElseThrow();
        RuleSet rules = ruleFactory.buildRuleSet(config.rules());
        List<LocalDate> dataDates = data.stream().map(DataFile::date).filter(Objects::nonNull).toList();

        Instant startedAt = clock.instant();
        String runId = RUN_ID_TIME.format(startedAt) + "-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            StrategyResult result = strategy.run(new StrategyRunContext(config, rules, data, mode));
            if (result == null) {
                throw new IllegalStateException(config.strategyClass() + " returned no result");
            }
            return new RunRecord(runId, RunStatus.COMPLETED, mode, config, startDate, endDate, dataDates,
                    result.metrics(), result.details(), result.warnings(), null, startedAt, clock.instant());
        } catch (RuntimeException e) {
            log.warn("Run {} of strategy {} v{} failed", runId, config.id(), config.version(), e);
            String message = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            return new RunRecord(runId, RunStatus.FAILED, mode, config, startDate, endDate, dataDates,
                    Map.of(), null, List.of(), message, startedAt, clock.instant());
        }
    }
}
