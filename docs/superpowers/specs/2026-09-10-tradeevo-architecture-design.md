# TradeEvo Architecture Design

**Date:** 2026-09-10
**Status:** Design approved, pending implementation plan
**Scope:** Core engine v1 — structure, domain model, and schemas. No validated trading logic.

---

## 1. Purpose and Scope

TradeEvo is an AI-powered trading strategy research and evolution platform. The
initial target is SPX 0DTE options with two strategies: BPSC (Bull Put Credit
Spread) and BCSC (Bear Call Credit Spread).

This document specifies the v1 architecture: project structure, core domain
models, strategy representation, backtest engine, result schema, and research
layer scaffolding. It deliberately does **not** specify validated trading logic.
Strategy documents shipped in v1 are illustrative and flagged non-tradeable.

### Non-goals for v1

- Real-time trading execution
- Live market data adapters (interfaces only)
- The automated AI research loop
- Any validated strategy parameters
- Frontend

---

## 2. Design Principles

These constrain every decision below.

1. Research, strategy logic, backtesting, and runtime execution stay separate.
2. The runtime must not require an LLM.
3. Raw historical market data is stored locally and reused for backtesting.
4. Backtesting uses sequential historical replay. Look-ahead bias must be
   structurally impossible, not merely avoided by discipline.
5. Strategy logic is configuration-driven, never hard-coded.
6. Strategies and conditions are versioned. An existing version is never
   overwritten.
7. Research results and strategy evolution are persistent local files.
8. No coupling to a specific LLM.
9. No coupling to a specific data provider.
10. Backtest results are structured so an independent AI can diagnose them.

### The separation rule

| Concern | Owner |
|---|---|
| Facts | Quantitative engine |
| Implementation | Coding agent |
| Research / diagnosis | LLM |

The LLM is never the source of truth for backtest calculations. The engine
reports evidence; it does not form conclusions.

---

## 3. Project Layout

Code lives under `backend/`. Everything an LLM independently inspects sits at the
repository root, where it is obvious and easy to enumerate.

```
TradeEvo/
├── backend/
│   ├── pyproject.toml
│   ├── tradeevo/
│   │   ├── domain/          # frozen dataclasses, zero dependencies
│   │   ├── features/        # FeatureEngine
│   │   ├── conditions/      # registry + condition implementations
│   │   ├── rules/           # roles + RuleEngine
│   │   ├── strategy/        # documents, loader, validator, md renderer
│   │   ├── risk/            # RiskEngine
│   │   ├── pricing/         # PricingModel interface + v1 implementation
│   │   ├── regime/          # regime classifier
│   │   ├── decision/        # DecisionEngine (shared: backtest + runtime)
│   │   ├── data/            # sources/, recorder, store, snapshot, realtime/
│   │   ├── backtest/        # ReplayEngine, PortfolioSimulator, ShadowPortfolio
│   │   ├── validation/      # oos, walkforward, robustness
│   │   ├── results/         # result schema + analyzers
│   │   ├── research/        # experiments, evolution memory
│   │   ├── runtime/         # live decision loop (no LLM)
│   │   └── cli.py
│   ├── api/                 # FastAPI surface for GEX ingestion (deferred to v2)
│   └── tests/
├── frontend/                # untouched, later
├── strategies/              # versioned strategy documents
│   ├── bpsc/v1/{strategy.json, strategy.md}
│   └── bcsc/v1/{strategy.json, strategy.md}
├── regimes/v1.json          # versioned regime classifier definition
├── experiments/             # experiment specs + results
├── research/memory/         # immutable evolution iterations
├── data/
│   ├── historical/daily/    # long-running daily series
│   └── historical/intraday/ # one file per trading day
└── docs/
```

`data/historical/` is gitignored. `strategies/`, `regimes/`, `experiments/`, and
`research/memory/` are tracked — they are research artifacts, not scratch data.

### Dependency posture

The core engine is **stdlib-only**: frozen dataclasses and stdlib `json`. No
pydantic, no pandas, no numpy in `tradeevo/` core modules. This is what makes the
engine genuinely reusable and separately executable, and it keeps test runs fast
and deterministic.

Provider-specific dependencies (`yfinance`, `psycopg`, `fastapi`) live only in
adapter modules under `data/sources/`, `data/realtime/`, and `backend/api/`. The
core defines protocols; adapters implement them. The core never imports an
adapter.

The package is installable and independently executable:

```
python -m tradeevo backtest --experiment experiments/exp-0001
```

---

## 4. Core Domain Model

All domain types are frozen dataclasses with no I/O and no behavior beyond pure
derivation.

### MarketSnapshot

The single input to the decision engine. Immutable, and the only market state the
engine ever sees.

```
MarketSnapshot:
    timestamp           # timezone-aware, America/New_York
    trading_date
    premarket_context   # PremarketContext for the day
    spx_bar             # most recent 5m bar at or before T
    indicators          # as-of VIX / ADD / TICK values with staleness info
    features            # FeatureSet
    regime              # RegimeLabel
```

The engine receives one snapshot at a time and holds no reference to the series.
**Look-ahead bias is therefore structurally impossible** rather than prevented by
convention.

### PremarketContext

Derived from the user-supplied pre-market payload (the `market_summary` block):

```
PremarketContext:
    timestamp
    previous_day:        spx{close, open, daily_high, daily_low}, vix, add_nyse
    premarket:           vix, spx_future
    gex_data:            net_gex_min, net_gex_max, gamma_regime, gamma_flip,
                         call_wall, put_wall,
                         largest_negative_gamma_strike,
                         largest_positive_gamma_strike
    dealer_positioning:  gamma_regime, volatility_behavior, expected_behavior
    options_flow:        put_call_ratio_oi, put_call_ratio_volume, skew,
                         zero_dte_dominance
    volatility_surface:  vix, iv_30d_range, iv_percentile, term_structure
```

Nullable fields (`put_call_ratio_oi`, `iv_percentile`) are represented as
`None`, never coerced to a default. See §9 on UNKNOWN handling.

### FeatureSet

A frozen, namespaced mapping from feature key to value, plus per-key provenance
(source timestamp and staleness). Conditions reference features by string key.

Namespaces: `spx.*`, `spy.*`, `vix.*`, `breadth.*`, `session.*`, `regime.*`,
`gex.*`.

Examples: `spx.price`, `spx.vwap`, `spx.rsi14`, `spx.ma20`, `vix.level`,
`vix.slope_5m`, `breadth.add`, `breadth.tick`, `session.minutes_since_open`,
`regime.gamma`, `gex.gamma_flip`.

### Signal types

```
ConditionResult:  condition_ref, status (PASS|FAIL|UNKNOWN), value, detail
RuleResult:       rule_id, role, condition_result, contribution
Decision:         timestamp, strategy_ref, action (ENTER|SKIP),
                  score, passed_rules, failed_rules, unknown_rules,
                  veto_reason, proposed_position
```

`Decision` is fully explainable and machine-readable. Every skip carries its
reason. This is what makes §5 and §8 of the result schema computable.

### Positions are not market data

`positions` is runtime/portfolio state and is deliberately **excluded** from
`MarketSnapshot`. During a backtest, position state comes from the simulated
portfolio, never from a data file.

---

## 5. Data Layer

### File formats

Two stores, because indicators have different horizons.

**Long-running daily series** — `data/historical/daily/spx.json`

Required because MA200 and daily RSI need history far beyond a single session.

```json
{ "symbol": "spx",
  "bars": [ { "d": "2026-09-09", "o": …, "h": …, "l": …, "c": …, "v": … } ] }
```

**One file per trading day** — `data/historical/intraday/YYYY-MM-DD.json`

```json
{
  "date": "2026-09-10",
  "session": { "open": "09:30", "close": "16:00", "tz": "America/New_York" },
  "premarket_context": { … },
  "spx":  { "candles_5m": [ { "t": "09:35", "o":…, "h":…, "l":…, "c":…,
                              "v":…, "vwap":… } ] },
  "spy":  { "candles_5m": [ … ] },
  "indicators": [ { "time": "09:35", "vix": 17.69, "add": -1064, "tick": -530 } ],
  "daily_stats": { … }
}
```

`candles_5m[].vwap` is cumulative session VWAP, not per-candle VWAP. Confirmed
by the final candle's VWAP matching `daily_stats.vwap`.

### Two look-ahead traps in the raw data

**Trap 1 — `daily_stats` contains end-of-day values.** In the sample payload
`daily_stats.h` is the full-day high and `daily_stats.vwap` is the 16:00 VWAP. A
condition reading these at 10:00 would see the future.

*Resolution:* the replay engine **never reads `daily_stats`**. The FeatureEngine
recomputes running price, high, low, and VWAP incrementally from candles at or
before T. `daily_stats` is retained in the file purely as a recorded end-of-day
summary and is used only by a validation test asserting that recomputation at
16:00 matches it. This is enforced by the loader, which does not expose
`daily_stats` on the replay path.

**Trap 2 — indicator series are irregular.** `indicators` entries appear off-grid
(07:43, 11:44, 15:59), skip timestamps (no 12:00), and have sparse fields — `add`
and `tick` are each missing at several entries.

*Resolution:* one explicit as-of join rule. For time T, take the most recent
observation at or before T. If that observation is older than a configured
staleness bound, the feature resolves to `UNKNOWN` rather than a stale value.
Staleness bound is per-feature and recorded in run config.

### Modules

```
data/sources/base.py         MarketDataSource protocol
data/sources/yahoo.py        SPX/SPY daily + intraday (stub in v1)
data/sources/tradingview.py  VIX, ADD, TICK (stub in v1)
data/sources/gex.py          user-submitted pre-market payload (stub in v1)
data/recorder.py             sources -> canonical daily JSON
data/store.py                load_day / list_days / hash_of / load_daily_series
data/snapshot.py             day file + T -> MarketSnapshot
data/realtime/postgres.py    live persistence (stub in v1)
```

`DataRecorder` writes **idempotently**: sorted keys, fixed float formatting,
stable ordering. Re-recording a day yields byte-identical output, so every day
file is content-hashable.

`dataset_hash` is a deterministic digest over the sorted set of day-file hashes
consumed by a run. This is the concrete meaning of "identify the exact data
snapshot used."

### GEX ingestion

A FastAPI endpoint in `backend/api/` accepts the pre-market payload, validates
it, and writes it into that day's file. The API imports the engine; the engine
never imports the API. Deferred to v2 — the payload schema and file writer exist
in v1, the HTTP surface does not.

---

## 6. Feature Engine

`FeatureEngine` converts the ordered stream of bars and indicator observations
into a `FeatureSet` at each step.

- **Forward-only.** It advances through time and never rewinds. Indicators are
  computed incrementally.
- **Reset per run.** Explicitly reset before each backtest so runs cannot leak
  state into one another.
- **Deterministic.** Given the same ordered input it produces identical output.
- **Seeded from the daily series.** MA and daily RSI warm up from
  `data/historical/daily/spx.json` before the session begins.

Feature computation is versioned. `feature_engine_version` is recorded in run
information, because changing how `spx.rsi14` is computed changes every
downstream result.

---

## 7. Pricing Model

v1 has no option chain data. Option prices are simulated.

```
PricingModel (protocol):
    price_spread(snapshot, spread_definition) -> SpreadQuote
```

**v1 implementation** — Black-Scholes with:
- underlying = current SPX level
- implied volatility = VIX-derived proxy
- time to expiry = time remaining to 16:00 on the same session (0DTE)
- risk-free rate = configured constant

The model is behind an interface so a real option chain drops in later without
touching the strategy, decision, or backtest layers.

**The pricing model is a result determinant.** Every P&L number in v1 comes from
it. `pricing_model` (identifier plus parameter hash) is therefore recorded
explicitly in run information, not folded opaquely into `config_hash`.

Simulated fills are approximations. Every result produced with the v1 model is
labeled as such.

---

## 8. Strategy Representation

### Versioned, immutable documents

```
strategies/bpsc/v1/strategy.json    # source of truth
strategies/bpsc/v1/strategy.md      # generated, documentation only
strategies/bpsc/v2/…                # a new version is a new directory
```

A version directory is written once and never modified. Each document is
content-hashed; the hash is recorded in every result that used it, so any result
traces back to the exact strategy bytes that produced it.

### Document shape

```json
{
  "strategy_id": "bpsc",
  "version": 1,
  "name": "Bull Put Credit Spread",
  "status": "illustrative",
  "tradeable": false,
  "spread": { "type": "put_credit", "width": 25, "short_delta_target": 0.20 },
  "conditions": [
    { "handle": "above_vwap",
      "condition_id": "price_vs_reference", "condition_version": 1,
      "params": { "feature": "spx.price", "reference": "spx.vwap", "op": ">" } },
    { "handle": "neg_gamma",
      "condition_id": "regime_equals", "condition_version": 1,
      "params": { "feature": "regime.gamma", "value": "negative" } },
    { "handle": "entry_window",
      "condition_id": "time_window", "condition_version": 1,
      "params": { "start": "10:00", "end": "14:30" } },
    { "handle": "vix_calm",
      "condition_id": "level_below", "condition_version": 1,
      "params": { "feature": "vix.level", "threshold": 20 } }
  ],
  "rules": [
    { "rule_id": "r1", "role": "REQUIRED", "condition": "above_vwap" },
    { "rule_id": "r2", "role": "VETO",     "condition": "neg_gamma" },
    { "rule_id": "r3", "role": "FILTER",   "condition": "entry_window" },
    { "rule_id": "r4", "role": "SCORE",    "condition": "vix_calm",
      "weight": 0.4 }
  ],
  "risk":  { … },
  "entry": { … },
  "exit":  { … }
}
```

Conditions are declared as named instances; rules reference them by handle. The
same condition type can appear multiple times with different parameters.

### The risk, entry, and exit blocks

```json
"risk": {
  "max_loss_per_trade": 500,
  "max_concurrent_positions": 1,
  "max_daily_loss": 1500,
  "contracts": 1
},
"entry": {
  "score_threshold": 0.6,
  "max_entries_per_day": 2,
  "min_credit": 0.50
},
"exit": {
  "profit_target_pct": 0.5,        // fraction of credit captured
  "stop_loss_multiple": 2.0,       // multiple of credit received
  "time_exit": "15:45",
  "hold_to_expiry": false
}
```

All values are parameters, not logic. `RiskEngine` and the exit evaluator read
them; neither contains strategy-specific branching. Values shown are illustrative
and unvalidated.

`exit_reason` is a closed vocabulary: `PROFIT_TARGET`, `STOP_LOSS`, `TIME_EXIT`,
`EXPIRY`, `RISK_LIMIT`, `END_OF_DATA`. `END_OF_DATA` marks a position still open
when the replay range ended and is excluded from win-rate calculations while
still appearing in `per_trade`.

### Condition contract

A condition is a **pure function of the FeatureSet** — no I/O, no clock, no
randomness, no access to the snapshot series.

```
Condition:
    condition_id
    version
    params_schema
    evaluate(features) -> ConditionResult
```

Purity is what makes conditions independently unit-testable and the whole engine
deterministic.

Conditions are registered by `(condition_id, version)`. **Changing logic creates
a new version**; the old version remains registered. Historical backtests
therefore keep reproducing exactly.

v1 condition library (approximately eight, covering the specified vocabulary):

| condition_id | Covers |
|---|---|
| `price_vs_reference` | SPX > VWAP, price vs moving average |
| `level_above` / `level_below` | RSI > threshold, VIX < threshold |
| `slope_below` / `slope_above` | VIX slope |
| `regime_equals` | volatility regime, gamma regime |
| `breadth_threshold` | market breadth (ADD, TICK) |
| `time_window` | time of day |
| `value_between` | ranged checks |

### Rule roles and evaluation order

Evaluation order is fixed and documented:

1. **FILTER** — determines whether this moment is a *candidate opportunity* at
   all. A FILTER failure means no opportunity existed, so it is **not** counted
   in `opportunities_detected` and generates no shadow trade. Time windows are
   the canonical use.
2. **VETO** — any veto condition passing causes immediate SKIP; the reason is
   recorded. The opportunity *is* counted and shadow-tracked.
3. **REQUIRED** — all required conditions must pass, otherwise SKIP. Counted and
   shadow-tracked.
4. **SCORE** — weighted contributions produce a normalized score; a configured
   threshold decides ENTER versus SKIP.

The FILTER/REQUIRED distinction is load-bearing for the result schema: FILTER
answers "was there anything to decide?", REQUIRED answers "should we have taken
it?". Conflating them would inflate `opportunities_detected` with every
off-hours bar and make skip-reason statistics meaningless.

Note that FILTER runs first despite being listed third in the role vocabulary —
ordering follows evaluation cost and semantics, not declaration order.

**Score normalization.** The score is the weight-weighted pass fraction:

```
score = Σ(weight_i × pass_i) / Σ(weight_i)      pass_i ∈ {0, 1}
```

UNKNOWN contributes 0 to the numerator while remaining in the denominator, so
missing data lowers the score rather than silently inflating it. The score is
therefore always in [0, 1] and comparable across strategies with different
numbers of SCORE rules. `entry.score_threshold` gates ENTER. When a strategy
declares no SCORE rules the score is 1.0.

The engine has no knowledge of what "BPSC" means. It resolves handles, evaluates
conditions, and applies roles. Adding a strategy requires no engine change.

### Generated markdown

`strategy.md` renders conditions, rules, roles, and parameters as readable
tables, prefixed with `<!-- GENERATED — do not edit -->`. Regenerated by
`python -m tradeevo strategy render`. Documentation only; the JSON is
authoritative. A test asserts the rendered output matches the document.

### v1 strategy documents

BPSC v1 and BCSC v1 ship as **illustrative** documents with
`"tradeable": false`, using plausible but entirely unvalidated conditions. Their
purpose is to exercise all four rule roles end to end. Real thresholds come from
research, not from authorship.

---

## 9. UNKNOWN Handling

Missing data must never silently evaluate as FALSE — that biases backtests in a
way no downstream analysis can detect.

`ConditionResult.status` is three-valued: `PASS`, `FAIL`, `UNKNOWN`.

Resolution by role:

| Role | UNKNOWN behavior |
|---|---|
| REQUIRED | Treated as not satisfied → SKIP, recorded as `unknown`, not `failed` |
| VETO | Fail-safe, configurable per rule: `veto` (default) or `ignore` |
| FILTER | Excludes the candidate, recorded separately from a FILTER failure |
| SCORE | Contributes zero; the count is recorded |

Every UNKNOWN is counted and attributed in `data_quality`, so a strategy problem
can be distinguished from a data problem.

---

## 10. Regime Classification

Breakdowns and per-trade records reference `market_regime` and
`volatility_regime`. These are only objective if their thresholds are explicit.

Regimes are therefore a **declarative, versioned document** —
`regimes/v1.json` — resolved the same way strategies are, with `regime_ref`
recorded in run information. Changing thresholds creates a new version and
cannot silently alter the meaning of historical results.

```json
{ "version": 1,
  "market_regimes": [
    { "id": "TRENDING_UP",
      "when": [ { "condition_id": "price_vs_reference", "condition_version": 1,
                  "params": { "feature": "spx.price", "reference": "spx.ma20",
                              "op": ">" } },
                { "condition_id": "price_vs_reference", "condition_version": 1,
                  "params": { "feature": "spx.price", "reference": "spx.vwap",
                              "op": ">" } } ] },
    { "id": "TRENDING_DOWN", "when": [ … ] },
    { "id": "RANGE",         "when": [] }
  ],
  "volatility_regimes": [
    { "id": "HIGH_VOLATILITY",        "when": [ … ] },
    { "id": "VOLATILITY_EXPANSION",   "when": [ … ] },
    { "id": "VOLATILITY_CONTRACTION", "when": [ … ] },
    { "id": "LOW_VOLATILITY",         "when": [] }
  ] }
```

Classification reuses the condition evaluation machinery, so regime rules are
expressed in the same vocabulary as strategy rules. A `when` array is an implicit
AND of its conditions.

Regimes are evaluated **in declaration order; the first match wins**. The final
entry in each list carries an empty `when` and acts as the catch-all default, so
classification is total — every bar receives exactly one market regime and one
volatility regime. If any condition in a `when` returns UNKNOWN, that regime does
not match and evaluation continues; a bar reaching the catch-all through UNKNOWN
paths is flagged in `data_quality`.

Threshold values in `regimes/v1.json` are placeholders, flagged unvalidated, and
are a research output rather than an authored constant.

---

## 11. Decision Engine and Runtime

```
DecisionEngine.decide(snapshot, strategy_document) -> Decision
```

Pure computation. No LLM, no network, no I/O. Backtest and runtime use the
**same** engine — only the snapshot source differs:

- `HistoricalReplaySource` — walks a day file forward
- `LiveSource` — builds snapshots from realtime data (deferred to v2)

This is what satisfies principle 2 automatically: the runtime cannot require an
LLM because the decision path contains no LLM call site.

---

## 12. Risk Engine

```
RiskEngine.evaluate(decision, snapshot, portfolio) -> ProposedPosition | Rejection
```

Parameter-driven from the strategy document's `risk` block: max loss per trade,
max concurrent positions, spread width, target credit, stop multiple, daily loss
limit. Rejections carry reasons and are recorded in `decision_analysis`.

---

## 13. Backtest / Replay Engine

At each 5-minute step T, `ReplayEngine`:

1. Builds a `MarketSnapshot` from data at or before T only.
2. As-of joins irregular indicator series with the staleness bound.
3. Advances the `FeatureEngine` forward.
4. Classifies the regime.
5. Calls `DecisionEngine`.
6. Applies `RiskEngine`.
7. Passes the result to `PortfolioSimulator` and `ShadowPortfolio`.

### ShadowPortfolio

The result schema requires `trades_when_false`, `win_rate_when_false`, and
`expectancy_when_false` per condition. These cannot be computed from trades that
were taken — they require simulating trades that were **declined**.

`ShadowPortfolio` therefore tracks **every detected opportunity** through to a
synthetic exit, regardless of whether it was entered, vetoed, or skipped. All
counterfactual statistics derive from that single pass.

This is what lets the research layer answer: *is this filter protecting the
strategy, or removing profitable trades?*

Shadow outcomes are clearly separated from realized outcomes in the result. They
are never mixed into `metrics`.

### Determinism

No wall clock, no unseeded randomness, sorted iteration throughout. Any seed used
is recorded in the experiment specification. Identical inputs produce
byte-identical results.

---

## 14. Result Schema

One schema for BACKTEST, OOS, WALK_FORWARD, and ROBUSTNESS.

The result is an **evidence report for AI analysis**, not a diagnosis. It must
not contain `diagnosis`, `recommended_strategy`, `recommended_parameter`,
`explanation`, `hypothesis`, or `final_decision`. Those belong to the research
layer.

### Storage

```
experiments/exp-0001/results/result.json    # compact evidence report
experiments/exp-0001/results/trades.jsonl   # detailed trade data, queried separately
```

The compact report is what a research agent normally receives. Detailed trade
data is queried only when deeper investigation is warranted.

### 14.1 Run information

```
run_id
run_type                 BACKTEST | OOS | WALK_FORWARD | ROBUSTNESS
created_at
parent_run_id            null unless this run belongs to an aggregate

strategy_ref: { id, version, hash }
dataset_hash
day_range
experiment_id
engine_version
feature_engine_version
pricing_model: { id, params_hash }
regime_ref: { version, hash }
config_hash
```

`pricing_model` and `regime_ref` are explicit rather than folded into
`config_hash`, because a diagnosing agent must be able to see which pricing
assumptions and regime definitions produced the numbers.

### 14.2 Core metrics

```
metrics:
    trades, win_rate, avg_pnl, expectancy, profit_factor,
    total_pnl, max_drawdown, sharpe, largest_loss, losing_streak
```

`metrics` always describes **this** run.

Definitions, pinned so results are comparable across versions:

| Metric | Definition |
|---|---|
| `win_rate` | winning trades ÷ total trades; a zero-P&L trade counts as neither |
| `avg_pnl` | mean realized P&L per trade |
| `expectancy` | `win_rate × avg_win + (1 − win_rate) × avg_loss`, avg_loss signed negative |
| `profit_factor` | gross profit ÷ absolute gross loss; `null` when gross loss is zero |
| `max_drawdown` | maximum peak-to-trough decline of the cumulative realized P&L curve, in currency |
| `sharpe` | mean ÷ stddev of **daily** P&L, annualized at √252; `null` below 20 days |
| `losing_streak` | longest run of consecutive losing trades in chronological order |

Sharpe is computed on daily rather than per-trade returns because 0DTE trade
counts per day vary; per-trade Sharpe would not be comparable across
configurations.

### 14.3 Breakdown

Same core metric subset across each dimension:

```
breakdown:
    by_year: [...]
    by_time_of_day: [...]          # 30-minute buckets across the session
    by_market_regime: [...]
    by_volatility_regime: [...]
```

Each entry carries: `trades`, `win_rate`, `expectancy`, `profit_factor`,
`total_pnl`, `max_drawdown`, plus `sample_size`.

Buckets below a configured minimum sample size are marked
`"status": "insufficient_data"` and their metrics are `null`, so small-n noise is
never presented as a finding.

### 14.4 Loss analysis

```
loss_analysis:
    losses_by_time:   [...]
    losses_by_regime: [...]
    losses_by_score:  [...]        # score buckets, boundaries recorded
    largest_losses:   [ { timestamp, pnl, score, regime, volatility_regime,
                          key_features } ]
```

`key_features` captures the market state at entry for each large loss:
`spx_vs_vwap`, `rsi`, `vix`, `vix_change`, `vix_slope`, `breadth_add`,
`volatility_regime`, `time_of_day`.

`largest_losses` length and the score-bucket boundaries are configured and
recorded.

### 14.5 Condition and rule statistics

Per condition:

```
condition_analysis:
    condition_id, condition_version
    evaluations, pass_rate, unknown_count

    trades_when_true,  win_rate_when_true,  expectancy_when_true
    trades_when_false, win_rate_when_false, expectancy_when_false
```

The `*_when_false` figures come from `ShadowPortfolio`, and the result marks
them as shadow-derived so they are never confused with realized performance.

Per selection-affecting rule:

```
veto_counts, skip_counts
```

These are counts against a denominator, so the section header carries it:

```
condition_analysis:
    opportunities_detected      # bars passing FILTER; denominator for the below
    trades_entered
    trades_skipped
    trades_vetoed
    conditions: [ … ]
```

`opportunities_detected` counts bars that passed FILTER — that is, moments where
a decision was genuinely available. Without it, `veto_counts` is an unanchored
number and a research agent cannot tell whether a veto fired on 2% or 80% of
opportunities.

### 14.6 Trade-level data

Compact report carries a summary; full records go to `trades.jsonl`.

```
per_trade:
    trade_id, entry_timestamp, exit_timestamp, pnl, score,
    regime, volatility_regime, exit_reason
```

`trades.jsonl` additionally carries entry/exit prices, credit, MFE, MAE, entry
conditions, violated conditions, and full market state at entry and exit —
enough for MFE/MAE analysis distinguishing poor entries from poor exits.

### 14.7 Daily data

```
per_day:
    date, trades, total_pnl, max_drawdown, regime, volatility_regime
```

### 14.8 Validation

```
validation:
    oos_metrics
    walk_forward_metrics
    robustness_metrics
    parameter_sensitivity
```

Populated **only on an aggregate run** that references child runs via
`parent_run_id`. On a single BACKTEST run this section is present with
`"status": "not_computed"`. Each sub-block contains only the core metric set.

`parameter_sensitivity` reports, per swept parameter, the range of expectancy
observed across perturbations and the fraction of perturbations that stayed
profitable.

### 14.9 Data quality

```
data_quality:
    unknown_feature_counts     # per feature key
    missing_data_count
    stale_data_count
    affected_trades
    affected_conditions
```

This is what allows a strategy problem to be distinguished from a data problem.

### 14.10 Baseline comparison

```
comparison:
    baseline_strategy_ref
    baseline_run_id
    delta: { trades, win_rate, expectancy, profit_factor,
             total_pnl, max_drawdown, sharpe }
```

Populated only when a baseline run is supplied; otherwise
`"status": "not_computed"`.

### Section status convention

Every section is **always present** and carries a status: `computed`,
`not_computed`, or `insufficient_data`. A section is never silently omitted.

A diagnosing agent must never have to guess whether a section is empty because
there was nothing to report or because nothing ran. This is the same reasoning as
UNKNOWN-versus-FALSE, applied at the report level.

---

## 15. Validation Drivers

All three drive the same `ReplayEngine` and emit the same schema.

- **`oos.py`** — non-overlapping in-sample and out-of-sample date ranges. An
  overlap is a hard error, not a warning.
- **`walkforward.py`** — rolling train/test windows with configurable width and
  step. Emits one child run per window plus an aggregate parent.
- **`robustness.py`** — parameter perturbation sweeps around a strategy's
  declared parameters, producing `parameter_sensitivity`.

---

## 16. Research Layer

### Experiments

The experiment is the reproducible unit.

```
experiments/exp-0001/
├── experiment.json
└── results/{result.json, trades.jsonl}
```

`experiment.json` declares: strategy_ref, day_range, run_type, OOS/walk-forward
splits, parameter sweeps, pricing model and parameters, regime_ref, staleness
bounds, minimum sample sizes, and any seed.

```
python -m tradeevo experiment run experiments/exp-0001
```

### Strategy evolution memory

```
research/memory/bpsc/
├── iteration-0001/{iteration.json, iteration.md}
├── iteration-0002/…
```

Append-only and immutable: an iteration directory is written once and never
modified. Each iteration links its parent, forming a chain.

```
iteration.json:
    iteration_id, parent_iteration_id, created_at
    current_strategy_ref
    observed_weaknesses
    research_objective
    hypothesis
    proposed_update
    updated_strategy_ref
    parameter_changes
    experiment_ref
    backtest_run_id, oos_run_id, walkforward_run_id, robustness_run_id
    comparison_with_previous
    conclusion
    decision
    next_research_direction
```

`iteration.md` is generated for reading; `iteration.json` is authoritative.

### The raw-data boundary

The research module's public API accepts **result objects only**. It has no
type-level path to a `MarketSnapshot`, a day file, or the data store. Principle
"do not send raw historical market data to an LLM" therefore holds structurally,
not by convention. A test asserts the research package does not import the data
package.

### Intended future workflow

```
Research Agent → Hypothesis → Experiment Specification
              → Coding Agent (implementation)
              → Backtest → Structured Results
              → Independent AI Diagnosis → Research Decision
              → Strategy Evolution
```

Claude Code is an implementation and experiment-execution agent, not the judge of
whether a strategy is good. An independent LLM inspects strategy definitions,
source, experiment specs, and all result files to produce a diagnosis. v1 builds
the artifacts this workflow consumes; it does not automate the loop.

---

## 17. Testing Strategy

`pytest`. No network access in any test.

**Two tests carry the architecture:**

1. **No-look-ahead.** Replay a day file truncated at T. Replay the full day file
   and stop at T. The decisions at T must be identical. This is the single test
   that keeps principle 4 honest, and it runs across several T values including
   session open and close.

2. **Determinism.** Run the same experiment twice and assert byte-identical
   `result.json` apart from `run_id` and `created_at`.

Supporting coverage:

- Table-driven unit tests per condition, including UNKNOWN paths
- Strategy document load/validate, including rejection of unknown condition ids,
  unknown handles, and duplicate rule ids
- Version immutability: writing to an existing version directory is an error
- Rule ordering: veto precedes required precedes filter precedes score
- `daily_stats` recomputation at 16:00 matches the recorded summary
- As-of join and staleness-bound behavior at gaps and off-grid timestamps
- Idempotent recorder output is byte-identical across runs
- Result schema completeness: every section present with a valid status
- Research package does not import the data package
- Rendered `strategy.md` matches its document

Sample day files are generated from the two supplied payloads and committed as
fixtures.

---

## 18. v1 Scope

**Built:**

- Domain models
- Feature engine
- Condition registry and ~8 conditions
- Rule engine, strategy engine, document loader/validator
- Markdown renderer
- Pricing model (Black-Scholes from SPX, VIX, time to expiry)
- Data store, snapshot builder, sample data fixtures
- Regime classifier and `regimes/v1.json`
- Replay engine, portfolio simulator, shadow portfolio
- Risk engine
- Decision engine
- Full result schema and analyzers
- OOS, walk-forward, robustness drivers
- Experiment runner
- Evolution memory structures
- BPSC and BCSC illustrative documents
- CLI
- Tests

**Deferred, with interfaces and extension points in place:**

- Live Yahoo and TradingView adapters (protocols defined, implementations stubbed)
- Postgres realtime persistence
- GEX REST endpoint (payload schema and writer exist; no HTTP surface)
- Frontend
- Live runtime source
- Any LLM agent or automated research loop
- Real option chain pricing

---

## 19. Extension Points

| Extension | Mechanism |
|---|---|
| New market | New data source adapter + feature namespace |
| New strategy | New JSON document; no engine change |
| New condition type | New registered class; existing versions untouched |
| Real option chain | New `PricingModel` implementation behind the same protocol |
| New data provider | Implement `MarketDataSource` |
| New regime definition | New `regimes/vN.json` |
| New diagnostic dimension | New breakdown analyzer emitting the standard metric set |
| Different LLM | Research layer reads result files; no LLM coupling exists |

---

## 20. Open Questions

Deferred deliberately; none block v1.

1. **Real option chain schema.** Defined when chain data becomes available.
2. **Score threshold calibration.** The ENTER threshold is a strategy parameter
   with no validated value. Research determines it.
3. **Exit modeling.** v1 exits are time-based and stop-based against simulated
   prices. Realistic fill modeling needs chain data.
4. **Regime threshold values.** `regimes/v1.json` ships with placeholder
   boundaries flagged as unvalidated.
5. **Multi-strategy portfolio interaction.** Out of scope; positions are tracked
   per strategy in v1.
