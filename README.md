# TradeEvo

**TradeEvo — Trading Strategy Evaluation & Evolution Engine**

## Purpose

TradeEvo is a backend engine designed to **evaluate, test, compare, and evolve trading strategies**.

The initial focus is on trading strategy research, with support for backtesting, strategy versioning, experiment tracking, and structured performance analysis.

TradeEvo does **not** collect market data, provide a user interface, execute real trades, or require an LLM at runtime. Data and AI-based research are handled by external applications.

## Core Workflow

```text
Strategy
   ↓
Data
   ↓
Execute / Backtest
   ↓
Evaluate
   ↓
Test Results
   ↓
Analyze / Diagnose
   ↓
Improve Strategy
   ↓
New Strategy Version
   ↓
Repeat
```

The core philosophy is:

> **Start Simple → Test → Diagnose → Improve → Repeat**

## Key Principles

- **Strategy-driven** — strategies are configurable and versioned.
- **Data-independent** — data is supplied by external sources.
- **Deterministic** — execution and evaluation do not depend on an LLM.
- **Evidence-based** — decisions are based on reproducible test results.
- **Experiment-oriented** — every meaningful strategy change can be tested and compared.
- **Extensible** — designed to support additional trading strategies and, eventually, other strategy domains.

## Initial Scope

The first version of TradeEvo will focus on **trading strategy research and backtesting**, providing the foundation for an external AI/research agent to iteratively discover and improve trading strategies.

---

## How it works

The core contains no market- or instrument-specific logic. It is a generic harness:

1. A **strategy config** (JSON) names a Java **strategy class** and lists **rules**. Each rule is a reusable core rule type plus parameters.
2. The engine validates the config, builds the rules, and triggers the strategy class with the **raw data**.
3. Each strategy defines **its own data format**. The strategy class reads the data, evaluates its rules against facts it derives, and **computes its own metrics**.
4. Results are stored with the exact config version used, so runs can be reproduced and compared.

Design spec: [`docs/superpowers/specs/2026-10-10-tradeevo-core-design.md`](docs/superpowers/specs/2026-10-10-tradeevo-core-design.md)

### Strategy config

```json
{
  "id": "threshold-signal",
  "strategyClass": "com.tradeevo.strategies.example.ThresholdSignalStrategy",
  "dataset": "example",
  "settings": { "closeAtEnd": true },
  "rules": [
    { "name": "entry", "rule": "Compare",
      "params": { "field": "x", "operator": ">", "value": 0.3 }, "action": "OPEN" },
    { "name": "exit", "rule": "Any",
      "params": { "rules": [
        { "rule": "Compare", "params": { "field": "pnl", "operator": ">=", "value": 2.0 } },
        { "rule": "Compare", "params": { "field": "pnl", "operator": "<=", "value": -1.0 } } ] },
      "action": "CLOSE" }
  ]
}
```

| Field | Meaning |
|---|---|
| `id` | Strategy id (letters, digits, `.`, `_`, `-`) |
| `version` | Assigned by TradeEvo (1, 2, …). Stored versions are immutable |
| `strategyClass` | Fully qualified name of a `Strategy` class in this project |
| `dataset` | Data folder under the data dir (default: `id`) |
| `settings` | Free-form, strategy-specific settings |
| `rules[]` | `name` (unique), `rule` (core rule type), `params`, optional `action` (interpreted by the strategy) |

Unknown fields are rejected, so typos can't slip through.

### Core rules

| Rule | Params | Matches when |
|---|---|---|
| `Compare` | `field`, `operator` (`> >= < <= == !=`), `value` *or* `otherField` | the comparison holds |
| `Between` | `field`, `min`, `max`, `inclusive` (default `true`) | the number is in range |
| `In` | `field`, `values[]` | the value is one of `values` |
| `TimeWindow` | `field`, `start`, `end` (`HH:mm`) | the time of day is in `[start, end)`; wraps past midnight |
| `All` / `Any` | `rules[]` of `{rule, params}` | all / any nested rules match |
| `Not` | `rule` | the nested rule does not match |

A missing fact, or one whose type doesn't fit, never matches. `GET /api/rules` returns every rule's parameter schema.

### Data

A backtest reads `{data-dir}/{dataset}/{yyyy-MM-dd}.json` for each date in the range. Dates without a file are skipped. An evaluation takes the same JSON in the request body instead. TradeEvo never interprets the contents; only the strategy class does.

### Adding a strategy

1. Create a `@Component` that implements `com.tradeevo.core.strategy.Strategy` under `com.tradeevo.strategies.<name>`.
2. Define its data format, read `context.data()`, build `Facts` per record, and use `context.rules()` (`matches`, `matching`, `anyMatches`).
3. Return a `StrategyResult` with numeric `metrics`, plus any `details` you want stored.
4. Optionally override `validate(config)` for required rules, actions or settings.

Then reference the class in a config. The engine needs no changes. See `strategies/example/ThresholdSignalStrategy` for a complete example.

## Build & run

Requires JDK 25. Maven is provided by the wrapper.

```bash
./mvnw verify                      # build + tests  (Windows: mvnw.cmd verify)
java -jar target/tradeevo.jar      # REST server on :8080
```

Configuration (`application.yml`, or `--tradeevo.data-dir=...` on the command line):

| Property | Default | |
|---|---|---|
| `tradeevo.data-dir` | `./data` | raw data files |
| `tradeevo.store-dir` | `./store` | `strategies/{id}/v{n}.json`, `runs/{runId}/result.json` |

## REST API

| Method & path | Purpose |
|---|---|
| `GET /api/rules`, `GET /api/rules/{name}` | Core rule types and their parameter schemas |
| `GET /api/strategy-classes` | Available strategy classes |
| `POST /api/strategies` | Create a strategy (version 1) |
| `POST /api/strategies/{id}/versions` | Save a changed config as the next version |
| `GET /api/strategies`, `GET /api/strategies/{id}` | List strategies / latest version |
| `GET /api/strategies/{id}/versions[/{v}]` | Version numbers / one version |
| `POST /api/backtests` | Run `{strategyId, version?, startDate, endDate}` over data files; result is stored |
| `GET /api/backtests[?strategyId=]`, `GET /api/backtests/{runId}` | Stored runs |
| `GET /api/backtests/compare?runIds=a,b` | Metrics side by side |
| `POST /api/evaluations` | Real-time: `{strategy, data}` → result, not stored |

`strategy` in an evaluation is either `{"strategyId": "...", "version": 2}` (version optional) or an inline config with `strategyClass`.

Status codes: 400 invalid config or request (`errors` lists each problem with its JSON path), 404 not found, 409 duplicate strategy, 422 the strategy failed while running (the response body is the FAILED run record).

```bash
curl -X POST localhost:8080/api/strategies -H 'Content-Type: application/json' --data @examples/threshold-signal.json
curl -X POST localhost:8080/api/backtests  -H 'Content-Type: application/json' \
     -d '{"strategyId":"threshold-signal","startDate":"2024-01-01","endDate":"2024-01-31"}'
```

## Command line

```bash
java -jar target/tradeevo.jar backtest --config examples/threshold-signal.json \
     --from 2024-01-01 --to 2024-01-31 --data-dir examples/data
java -jar target/tradeevo.jar backtest --strategy-id threshold-signal [--strategy-version 2] \
     --from 2024-01-01 --to 2024-01-31
```

The command prints a metrics summary and writes the full result to `--out` (default `results/`). Runs of stored strategies are also saved in the store. Exit codes: `0` completed, `1` the strategy failed, `2` invalid input.
