package com.tradeevo.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeevo.core.engine.RunRecord;
import com.tradeevo.core.engine.RunStatus;
import com.tradeevo.service.EvaluationRequest;
import com.tradeevo.service.EvaluationService;

/** Real-time evaluation of a strategy on data in the request body. Nothing is stored. */
@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluations;

    public EvaluationController(EvaluationService evaluations) {
        this.evaluations = evaluations;
    }

    /** 200 with the record; 422 with the record if the strategy failed. */
    @PostMapping
    public ResponseEntity<RunRecord> evaluate(@RequestBody EvaluationRequest request) {
        RunRecord record = evaluations.evaluate(request);
        HttpStatus status = record.status() == RunStatus.COMPLETED ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(record);
    }
}
