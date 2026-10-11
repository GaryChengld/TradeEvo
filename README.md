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
4. Results are stored with the exact config version used and a **manifest** (engine build, strategy hash, data hashes, execution assumptions), so runs can be reproduced and compared.

The code is split into two Maven modules:

| Module | Contents |
|---|---|
| `tradeevo-core` | Plain Java library with no Spring: rules, strategy contract, engine, data loading, JSON file stores, run comparison. Use it without Spring via `StrategyEngine.create(List.of(new MyStrategy()))`. |
| `tradeevo-app` | Spring Boot REST API and CLI around the core, plus the strategy implementations (`com.tradeevo.strategies.*`). |

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
| `rules[]` | `name` (unique), `rule` (core rule type), `params`, optional `action` (interpreted by the strategy), optional `onUnknown` |

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

`GET /api/rules` returns every rule's parameter schema.

#### Missing data: three-valued rules

Rules evaluate to `TRUE`, `FALSE` or `UNKNOWN`. A fact that is missing, null, NaN or infinite, of an incompatible type, or unparseable gives `UNKNOWN`. Numbers compare by exact decimal value, so large longs and `BigDecimal`s keep their precision. JSON decimals are read as doubles, so precision beyond a double is already gone after parsing. It is never silently `FALSE`, so `Not(rsi > 55)` with no RSI is still `UNKNOWN`, not a match.

| | `All` | `Any` | `Not` |
|---|---|---|---|
| result | FALSE if any is FALSE, else UNKNOWN if any is UNKNOWN, else TRUE | TRUE if any is TRUE, else UNKNOWN if any is UNKNOWN, else FALSE | UNKNOWN stays UNKNOWN |

Each top-level rule's `onUnknown` decides what an `UNKNOWN` outcome means for the strategy:
- `NO_MATCH` (default): treated as not matching.
- `MATCH`: treated as matching.
- `FAIL`: fails the run, naming the rule and its missing facts.

Every run records `ruleStats` (`matched` / `notMatched` / `unknown` per rule), with a warning for each rule that was ever UNKNOWN.

### Data

A backtest reads `{data-dir}/{dataset}/{yyyy-MM-dd}.json` for each date in the range. An evaluation takes the same JSON in the request body instead. TradeEvo never interprets the contents; only the strategy class does.

**Coverage.** Ideally the data provider writes a file for every calendar day:

| File for the day | Effect |
|---|---|
| Session data (any JSON the strategy understands) | Passed to the strategy |
| `{"session": "closed", "reason": "..."}` | Non-trading day: skipped, recorded in the manifest's `closedDates` |
| No file | Skipped, but recorded in `missingDates` with a run warning, since it may be an accidentally missing trading day |

Closed and missing dates are part of `dataHash`, and `compare` reports runs whose `missingDates` differ. A range with no session data at all returns 404.

Strategies should run record timestamps through `core.data.TimeOrder`, as the example does. Times must strictly increase across the whole run, including across file boundaries. Out-of-order, duplicate, missing or unparseable times fail the run with the record's location. Data is never reordered silently.

### Reproducibility and comparison

Each run record contains a `manifest`:

| Field | Meaning |
|---|---|
| `engineVersion`, `engineCommit`, `engineDirty` | Build that ran it (Maven version, git commit, whether there were uncommitted changes) |
| `strategyHash` | SHA-256 of the behavior-relevant config (`strategyClass`, `dataset`, `settings`, `rules`; not id, version or description) |
| `dataFiles[]`, `dataHash` | SHA-256 of each input file's canonical JSON, and of the whole list. Editing a data file changes the hash |
| `assumptions` | Execution assumptions declared by the strategy |

`GET /api/backtests/compare` checks runs against the first one (dataset, period, `dataHash`, engine build, assumptions, status, manifest present). It returns `comparable` and a list of `issues`; with `strict=true` it returns 409 when they aren't comparable. Compare strategy versions only when `comparable` is true.

### Adding a strategy

1. Create a `@Component` that implements `com.tradeevo.core.strategy.Strategy` under `com.tradeevo.strategies.<name>` in `tradeevo-app`.
2. Define its data format, read `context.data()`, build `Facts` per record, and use `context.rules()` (`matches`, `matching`, `anyMatches`).
3. Return a `StrategyResult` with numeric `metrics`, any `details` you want stored, and `assumptions` describing how the metrics were produced. A strategy that reports trading results must model execution, sizing, costs and slippage and declare them there. The example strategy is signal-only (`valueChange*` metrics are raw changes in a value, not P&L).
4. Optionally override `validate(config)` for required rules, actions or settings.

Then reference the class in a config. The engine needs no changes. See `tradeevo-app/.../strategies/example/ThresholdSignalStrategy` for a complete example.

## Build & run

Requires JDK 25. Maven is provided by the wrapper.

```bash
./mvnw verify                                # build + tests of both modules  (Windows: mvnw.cmd verify)
java -jar tradeevo-app/target/tradeevo.jar   # REST server on :8080
```

Configuration (`application.yml`, or `--tradeevo.data-dir=...` on the command line):

| Property | Default | |
|---|---|---|
| `tradeevo.data-dir` | `./data` | raw data files |
| `tradeevo.store-dir` | `./store` | `strategies/{id}/v{n}.json`, `runs/{runId}/result.json` |

Store files are written atomically and never overwritten. They are written to a temp file, then published with a hard link, which is atomic and create-only. The store directory must therefore be on a filesystem with hard links (NTFS, ext4, XFS, APFS…). There is no weaker fallback: on a filesystem without them (e.g. FAT32, some network shares), writes fail with an explicit error.

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
| `GET /api/backtests/compare?runIds=a,b[&strict=true]` | Metrics side by side, plus `comparable` and `issues`; with `strict`, 409 if not comparable |
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
java -jar tradeevo-app/target/tradeevo.jar backtest --config examples/threshold-signal.json \
     --from 2024-01-01 --to 2024-01-07 --data-dir examples/data
java -jar tradeevo-app/target/tradeevo.jar backtest --strategy-id threshold-signal [--strategy-version 2] \
     --from 2024-01-01 --to 2024-01-31
```

The command prints a metrics summary and writes the full result to `--out` (default `results/`). Runs of stored strategies are also saved in the store. Exit codes: `0` completed, `1` the strategy failed, `2` invalid input.
