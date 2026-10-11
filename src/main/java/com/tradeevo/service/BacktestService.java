package com.tradeevo.service;

import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.tradeevo.core.data.DataFile;
import com.tradeevo.core.data.DataRepository;
import com.tradeevo.core.engine.RunRecord;
import com.tradeevo.core.engine.StrategyEngine;
import com.tradeevo.core.error.BadRequestException;
import com.tradeevo.core.strategy.RunMode;
import com.tradeevo.core.strategy.StrategyConfig;
import com.tradeevo.store.RunStore;

/** Runs strategies over data files in a date range and keeps the results. */
@Service
public class BacktestService {

    private final StrategyService strategies;
    private final StrategyEngine engine;
    private final DataRepository dataRepository;
    private final RunStore runs;

    public BacktestService(StrategyService strategies, StrategyEngine engine, DataRepository dataRepository, RunStore runs) {
        this.strategies = strategies;
        this.engine = engine;
        this.dataRepository = dataRepository;
        this.runs = runs;
    }

    /** Runs a stored strategy version and stores the result (also when the run failed). */
    public RunRecord run(BacktestRequest request) {
        StrategyConfig config = strategies.get(request.strategyId(), request.version());
        return runs.save(execute(config, request.startDate(), request.endDate(), dataRepository));
    }

    /** Validates the config, loads its dataset from {@code data} and runs it. Does not store the result. */
    public RunRecord execute(StrategyConfig config, LocalDate startDate, LocalDate endDate, DataRepository data) {
        engine.requireValid(config);
        List<DataFile> files = data.load(config.effectiveDataset(), startDate, endDate);
        return engine.run(config, files, RunMode.BACKTEST, startDate, endDate);
    }

    public RunRecord get(String runId) {
        return runs.get(runId);
    }

    public List<RunRecord> list(String strategyId) {
        return runs.list(strategyId);
    }

    public RunComparison compare(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            throw new BadRequestException("runIds: at least one run id is required");
        }
        List<RunRecord> records = runIds.stream().map(runs::get).toList();
        TreeSet<String> names = new TreeSet<>();
        records.forEach(run -> names.addAll(run.metrics().keySet()));
        List<RunComparison.ComparedRun> compared = records.stream()
                .map(run -> new RunComparison.ComparedRun(run.runId(), run.strategy().id(), run.strategy().version(),
                        run.status(), run.startDate(), run.endDate(), run.metrics()))
                .toList();
        return new RunComparison(List.copyOf(names), compared);
    }
}
