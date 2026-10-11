package com.tradeevo.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradeevo.core.engine.RunComparison;
import com.tradeevo.core.engine.RunRecord;
import com.tradeevo.core.engine.RunStatus;
import com.tradeevo.service.BacktestRequest;
import com.tradeevo.service.BacktestService;

import jakarta.validation.Valid;

/** Backtests over stored data files, and the stored results. */
@RestController
@RequestMapping("/api/backtests")
public class BacktestController {

    private final BacktestService backtests;

    public BacktestController(BacktestService backtests) {
        this.backtests = backtests;
    }

    /** Runs synchronously. 201 with the record; 422 with the (stored) record if the strategy failed. */
    @PostMapping
    public ResponseEntity<RunRecord> run(@Valid @RequestBody BacktestRequest request) {
        RunRecord record = backtests.run(request);
        HttpStatus status = record.status() == RunStatus.COMPLETED ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status)
                .location(URI.create("/api/backtests/" + record.runId()))
                .body(record);
    }

    @GetMapping
    public List<RunRecord> list(@RequestParam(required = false) String strategyId) {
        return backtests.list(strategyId);
    }

    /**
     * Metrics side by side, with {@code comparable} and the {@code issues} found. With
     * {@code strict=true}, runs that are not comparable give 409 (the body still lists the issues).
     */
    @GetMapping("/compare")
    public ResponseEntity<RunComparison> compare(@RequestParam List<String> runIds,
            @RequestParam(defaultValue = "false") boolean strict) {
        RunComparison comparison = backtests.compare(runIds);
        HttpStatus status = strict && !comparison.comparable() ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(status).body(comparison);
    }

    @GetMapping("/{runId}")
    public RunRecord get(@PathVariable String runId) {
        return backtests.get(runId);
    }
}
