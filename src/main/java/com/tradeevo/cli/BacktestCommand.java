package com.tradeevo.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Callable;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeevo.core.data.DataRepository;
import com.tradeevo.core.engine.RunRecord;
import com.tradeevo.core.engine.RunStatus;
import com.tradeevo.core.error.InvalidConfigException;
import com.tradeevo.core.error.TradeEvoException;
import com.tradeevo.core.strategy.StrategyConfig;
import com.tradeevo.service.BacktestService;
import com.tradeevo.service.StrategyService;
import com.tradeevo.store.RunStore;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * {@code backtest} command. Exit codes: 0 completed, 1 the strategy failed, 2 invalid input
 * (config, arguments, missing data). Prototype-scoped: picocli writes options into fields, so each
 * execution needs a fresh instance.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Command(name = "backtest", description = "Run a strategy over data files in a date range.")
public class BacktestCommand implements Callable<Integer> {

    static final int EXIT_FAILED = 1;
    static final int EXIT_INVALID = 2;

    static class StrategySource {
        @Option(names = "--config", required = true, description = "Strategy config JSON file.")
        Path config;

        @Option(names = "--strategy-id", required = true, description = "Stored strategy id.")
        String strategyId;
    }

    @ArgGroup(exclusive = true, multiplicity = "1")
    StrategySource source;

    @Option(names = "--strategy-version", description = "Stored strategy version (default: latest).")
    Integer strategyVersion;

    @Option(names = "--from", required = true, description = "First date, yyyy-MM-dd.")
    LocalDate from;

    @Option(names = "--to", required = true, description = "Last date, yyyy-MM-dd.")
    LocalDate to;

    @Option(names = "--data-dir", description = "Data directory (default: tradeevo.data-dir).")
    Path dataDir;

    @Option(names = "--out", defaultValue = "results", description = "Directory for the result JSON (default: ${DEFAULT-VALUE}).")
    Path out;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help.")
    boolean help;

    @Spec
    CommandSpec spec;

    private final BacktestService backtests;
    private final StrategyService strategies;
    private final RunStore runStore;
    private final DataRepository defaultData;
    private final ObjectMapper mapper;

    public BacktestCommand(BacktestService backtests, StrategyService strategies, RunStore runStore,
            DataRepository defaultData, ObjectMapper mapper) {
        this.backtests = backtests;
        this.strategies = strategies;
        this.runStore = runStore;
        this.defaultData = defaultData;
        this.mapper = mapper;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        try {
            boolean stored = source.strategyId != null;
            StrategyConfig config = stored
                    ? strategies.get(source.strategyId, strategyVersion)
                    : mapper.readValue(source.config.toFile(), StrategyConfig.class);
            DataRepository data = dataDir != null ? new DataRepository(dataDir, mapper) : defaultData;

            RunRecord record = backtests.execute(config, from, to, data);
            if (stored) {
                runStore.save(record);
            }
            Path resultFile = this.out.resolve(record.runId() + ".json");
            Files.createDirectories(this.out);
            mapper.writerWithDefaultPrettyPrinter().writeValue(resultFile.toFile(), record);

            printSummary(out, record, resultFile);
            return record.status() == RunStatus.COMPLETED ? 0 : EXIT_FAILED;
        } catch (InvalidConfigException e) {
            err.println("Invalid strategy config:");
            e.getErrors().forEach(error -> err.println("  - " + error));
            return EXIT_INVALID;
        } catch (TradeEvoException e) {
            err.println("Error: " + e.getMessage());
            return EXIT_INVALID;
        } catch (IOException e) {
            err.println("Error: " + e.getMessage());
            return EXIT_INVALID;
        } finally {
            out.flush();
            err.flush();
        }
    }

    private static void printSummary(PrintWriter out, RunRecord record, Path resultFile) {
        StrategyConfig strategy = record.strategy();
        out.printf("Run       %s  %s%n", record.runId(), record.status());
        out.printf("Strategy  %s%s (%s)%n", strategy.id(),
                strategy.version() != null ? " v" + strategy.version() : "", strategy.strategyClass());
        out.printf("Data      %d file(s), %s to %s%n", record.dataDates().size(), record.startDate(), record.endDate());
        if (record.error() != null) {
            out.printf("Error     %s%n", record.error());
        }
        if (!record.metrics().isEmpty()) {
            out.println("Metrics");
            int width = record.metrics().keySet().stream().mapToInt(String::length).max().orElse(0);
            for (Map.Entry<String, Number> metric : record.metrics().entrySet()) {
                out.printf("  %-" + width + "s  %s%n", metric.getKey(), metric.getValue());
            }
        }
        record.warnings().forEach(warning -> out.println("Warning   " + warning));
        out.printf("Result    %s%n", resultFile);
    }
}
