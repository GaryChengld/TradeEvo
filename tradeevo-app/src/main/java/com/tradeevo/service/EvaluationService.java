package com.tradeevo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tradeevo.core.data.DataFile;
import com.tradeevo.core.engine.RunRecord;
import com.tradeevo.core.engine.StrategyEngine;
import com.tradeevo.core.error.BadRequestException;
import com.tradeevo.core.strategy.RunMode;
import com.tradeevo.core.strategy.StrategyConfig;

/** Evaluates a strategy on data supplied in the request. Nothing is stored. */
@Service
public class EvaluationService {

    private final StrategyResolver resolver;
    private final StrategyEngine engine;

    public EvaluationService(StrategyResolver resolver, StrategyEngine engine) {
        this.resolver = resolver;
        this.engine = engine;
    }

    public RunRecord evaluate(EvaluationRequest request) {
        if (request == null) {
            throw new BadRequestException("request body is required");
        }
        StrategyConfig config = resolver.resolve(request.strategy());
        if (request.data() == null || request.data().isNull() || request.data().isMissingNode()) {
            throw new BadRequestException("data: required");
        }
        return engine.run(config, List.of(new DataFile(null, request.data())), RunMode.EVALUATION, null, null);
    }
}
