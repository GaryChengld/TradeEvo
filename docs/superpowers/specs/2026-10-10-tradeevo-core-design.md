# TradeEvo Core — Generic, JSON-Configured Strategy Test Engine (v1)

## Context
The repo was cleared (only `.gitignore` left; old spec archived in commit `3f19aea`) to start a new design. TradeEvo is a **backend engine** that evaluates, tests, compares and evolves strategies. It does not collect data, has no UI, does not trade live and does not use an LLM at runtime. An external AI/research agent drives the loop *Strategy → Data → Test → Evaluate → Diagnose → New version → Repeat* through a **REST API** and a **command line** tool.

**The core has no stock or trading logic.** It is a generic harness:
- A **strategy config JSON** names a Java strategy class and lists **rules**. Each rule is a reusable core rule class plus parameters.
- The engine loads the config, builds the rules, triggers the strategy class with the raw data, and stores the result.
- **Each strategy defines its own raw-data JSON format.** Only the strategy class knows how to read it.
- **The strategy class computes its own result and metrics.** The engine stores, versions and compares results generically.
- Strategy classes live in this project. A new strategy means adding a class to the repo.

Stack: Java 25, Spring Boot 3.5.x, Maven (via Maven Wrapper), picocli for the CLI, one module. Storage: JSON files on disk.

## Strategy config (stored as `{storeDir}/strategies/{id}/v{n}.json`)
```json
{
  "id": "my-strategy",
  "version": 3,
  "description": "…",
  "strategyClass": "com.tradeevo.strategies.example.ThresholdSignalStrategy",
  "dataset": "example",
  "settings": { "anything": "strategy-specific, free-form" },
  "rules": [
    { "name": "entry", "rule": "Compare",
      "params": { "field": "x", "operator": ">", "value": 0.3 }, "action": "OPEN" },
    { "name": "exit", "rule": "Any",
      "params": { "rules": [
        { "rule": "Compare", "params": { "field": "pnl", "operator": ">=", "value": 0.5 } },
        { "rule": "Compare", "params": { "field": "pnl", "operator": "<=", "value": -2.0 } } ] },
      "action": "CLOSE" }
  ]
}
```
- `rule` is a registered core rule type, and `params` are validated against its schema.
- `action` is an opaque string that the strategy class interprets.
- `dataset` selects the data folder (it defaults to `id`).

## Core rule library (reusable, domain-free)
- `Rule` interface: `boolean evaluate(Facts facts)`. `Facts` is a read-only `Map<String, Object>` the strategy builds from its own data (e.g. `x`, `pnl`, `time`).
- `RuleType`: a Spring bean with `name()`, `description()`, `paramSchema()` and `create(params, RuleFactory)`. It is registered in `RuleRegistry`.
- v1 core rules:
  - `Compare` (field, operator `> >= < <= == !=`, value or `otherField`)
  - `Between` (field, min, max, inclusive)
  - `In` (field, values[])
  - `TimeWindow` (field, start, end as HH:mm or ISO time)
  - Composites `All`, `Any`, `Not`, which nest rule configs
- `RuleSet`: the built rules for a config. `matches(name, facts)`, `matching(facts)` returns the matched named rules with their actions, and `get(name)`.
- `ParamSchema`: typed specs (name, type, required, default, min/max, description) and `validate(params)`. All errors are collected with their JSON path, e.g. `rules[1].params.rules[0].field: required`.

## Layout
```
pom.xml                      # spring-boot-starter-web, -validation, picocli, spring-boot-starter-test
README.md                    # user's README text + config/rules/API/CLI usage
docs/superpowers/specs/2026-10-10-tradeevo-core-design.md
examples/                    # example strategy config + example data files
src/main/java/com/tradeevo/
  TradeEvoApplication.java   # args[0]=="backtest" → CLI mode (web disabled), else REST server
  config/TradeEvoProperties  # tradeevo.data-dir, tradeevo.store-dir
  core/strategy/Strategy               # StrategyResult run(StrategyRunContext ctx); default validate(config)
  core/strategy/StrategyRunContext     # config, settings (JsonNode), RuleSet, data: List<DataFile(date, JsonNode)>,
                                       # runMode (BACKTEST | EVALUATION)
  core/strategy/StrategyResult         # metrics: Map<String, Number>, details: JsonNode (strategy-defined), warnings
  core/strategy/StrategyClassRegistry  # all Strategy beans in this project, keyed by fully qualified class name
  core/strategy/StrategyConfig         # record matching the JSON above (+ RuleConfig)
  core/rule/                           # Rule, Facts, RuleType, RuleRegistry, RuleFactory, RuleSet, ParamSchema
  core/rule/types/                     # Compare, Between, In, TimeWindow, All, Any, Not
  core/engine/StrategyEngine           # validate config → build RuleSet → resolve class → run → wrap result in a RunRecord
  core/data/DataRepository             # {dataDir}/{dataset}/{yyyy-MM-dd}.json for a date range, as raw JsonNode
  store/StrategyStore                  # immutable versions: create, newVersion (n+1), get, list
  store/RunStore                       # {storeDir}/runs/{runId}/result.json (config snapshot, dates, metrics, details)
  service/StrategyResolver             # stored {strategyId, version} or inline config → validated StrategyConfig
  service/BacktestService              # resolve → load data files by date → engine → persist
  service/EvaluationService            # resolve → engine on the raw data in the request → nothing persisted
  api/CatalogController                # GET /api/rules, GET /api/rules/{name} (param schemas); GET /api/strategy-classes
  api/StrategyController               # POST /api/strategies, POST /api/strategies/{id}/versions, GET list/latest/version
  api/BacktestController               # POST /api/backtests {strategyId, version?, startDate, endDate} → result
                                       # GET /api/backtests/{runId}, GET /api/backtests?strategyId=
                                       # GET /api/backtests/compare?runIds=a,b → metrics side by side (union of keys)
  api/EvaluationController             # POST /api/evaluations {strategy: {strategyId, version} | <inline config>,
                                       #   data: <raw JSON in the strategy's own format>} → result, real time, not stored
  api/ApiExceptionHandler              # ProblemDetail: 400 for invalid config/rules/data, 404 for missing strategy/run/data
  cli/BacktestCommand                  # backtest (--config f.json | --strategy-id X [--version N]) --from D --to D
                                       #   [--data-dir] [--out dir]; prints metrics, writes result JSON, exit≠0 on error
  strategies/example/ThresholdSignalStrategy  # ONE example strategy (not core): reads its own format
                                       #   {"records":[{"time":…, "x":…}]}, builds Facts per record, applies the entry/exit
                                       #   rules, and reports simple metrics (signals, opens, closes) to prove the pipeline
```
Adding a strategy: create a `@Component` class implementing `Strategy` under `com.tradeevo.strategies.<name>`, define its data format, and reference its class name in a config JSON. No engine changes are needed.

## Key behaviors
- **Determinism:** the engine adds no randomness or wall-clock time to strategy inputs. The same config version and data give the same result. `runId` and timestamps are stored only in the run record's metadata.
- **Validation before a run:** the strategy class must exist and implement `Strategy`. Every rule type must exist and its params must pass the schema. The strategy's own `validate(config)` is also called. All errors are reported together.
- **Data:** missing date files are skipped. If no file exists in the range → 404. Invalid JSON → 400 naming the file. The engine never interprets the data contents.
- **Versioning:** new params or rules → `v{n+1}`. Existing versions are never overwritten. Each run stores a snapshot of the config it used.
- **Errors inside a strategy:** an exception from `run()` becomes a failed run record (status FAILED, message) and a 422 response.

## Housekeeping
- `.gitignore`: `target/`, `data/`, `store/`, IDE files.
- Commits: spec + README → scaffold → rule library → engine/registries/data → store/services → API → CLI → example strategy.

## Verification
1. `mvn verify` runs all tests with JUnit 5:
   - Rule tests for each core rule (including nesting, missing fields and type mismatches) and `ParamSchemaTest` (defaults, required, min/max, unknown params, error paths).
   - `RuleSetTest`: building from config, `matching()` order and actions.
   - `StrategyEngineTest`: unknown class / unknown rule / bad params → collected errors; a strategy exception → FAILED; a test-only strategy receives the expected context.
   - `DataRepositoryTest`: date range, skipped dates, invalid JSON.
   - `StrategyStoreTest` / `RunStoreTest`: versioning and immutability.
   - MockMvc: catalog → create strategy → new version → backtest → get → compare; evaluation with inline data leaves the store unchanged; 400/404/422 cases.
   - `ThresholdSignalStrategyTest` plus a determinism test (two identical runs → equal results).
2. Manual end to end with `examples/`:
   - `mvn spring-boot:run`, then `curl` GET `/api/rules`, POST the example config, POST `/api/backtests` with a date range, POST `/api/evaluations` with inline data.
   - `java -jar target/tradeevo.jar backtest --config examples/threshold-signal.json --from … --to … --data-dir examples/data` prints metrics and writes the result JSON.

## Revisions (2026-10-11): fixes from code review

- **Modules:** `tradeevo-core` is a plain Java library with no Spring dependency (rules, strategy contract, engine, data, stores, comparison; `CoreRuleTypes.builtIns()`, `StrategyEngine.create(...)`). `tradeevo-app` holds the Spring REST API, CLI and strategies, and wires the core in `CoreConfiguration`. `CoreHasNoSpringTest` guards the boundary.
- **Atomic writes:** `JsonFiles.writeNew` writes and syncs a temp file, then publishes it with a hard link (atomic, create-only), falling back to a non-replacing move. A failure never leaves a partial version or result.
- **Reproducibility:** `RunRecord.manifest` holds the engine version, git commit and dirty flag, `strategyHash` (behavior-relevant config only), per-file canonical-JSON SHA-256s, `dataHash`, and the strategy's `assumptions`. Hashes are taken before the strategy runs.
- **Three-valued rules:** `Truth {TRUE, FALSE, UNKNOWN}` with Kleene All/Any/Not. A missing or incompatible fact is UNKNOWN. Per-rule `onUnknown`: NO_MATCH (default), MATCH or FAIL. `RunRecord.ruleStats` counts outcomes, with a warning for each rule that was UNKNOWN.
- **Time order:** the `core.data.TimeOrder` utility rejects missing, unparseable, mixed local/offset, duplicate or decreasing times, across file boundaries. The example strategy requires it, so bad data fails the run.
- **Immutability:** JSON nodes in `StrategyConfig`, `RuleConfig`, `StrategyResult`, `RunRecord` and `RunManifest` are deep-copied on construction and on access.
- **Signals vs P&L:** `StrategyResult.assumptions` declares the execution model. The example strategy is labeled signal-only and its metrics were renamed (`valueChangeTotal`, `valueChangeAverage`, `positiveSignalRate`).
- **Comparable comparisons:** `RunComparator` flags differences in dataset, period, `dataHash`, engine build, assumptions, status or a missing manifest. `compare` returns `comparable` and `issues`; `strict=true` turns a non-comparable result into 409.
