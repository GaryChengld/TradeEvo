# TradeEvo — Design, Architecture & Tech Stack

**Design idea.** TradeEvo is a backend-only engine to evaluate, test, compare and evolve trading strategies. Loop: Strategy → Data → Backtest → Evaluate → Diagnose → Improve → New version → Repeat. It has no data collection, no UI, no live trading and no runtime LLM. Principles: strategy-driven, data-independent, deterministic, evidence-based, experiment-oriented, extensible. It must work for any strategy and instrument (not only SPX options), and the core contains **no stock/instrument logic**.

**Strategy model:**
- A strategy is a JSON config naming a Java `strategyClass`. The engine invokes that class.
- Rules live in the JSON as reusable core rule types (Compare, Between, In, TimeWindow, All, Any, Not), configured by rule name plus params.
- Each strategy defines its own raw-data JSON format, and only its class understands it. The class computes its own metrics and declares `assumptions` (execution, costs, sizing, units).
- New strategy = add a class to this repo (`tradeevo-app/.../strategies/`). No plugins.
- Data: one file per calendar day, `{data-dir}/{dataset}/{yyyy-MM-dd}.json`. `{"session":"closed"}` marks a non-trading day. A missing file is skipped, recorded in `missingDates` and warned about.
- Storage: JSON files on disk with atomic, create-only writes (temp file + fsync + hard link). There is no fallback, so the store needs a filesystem that supports hard links.
- Rules are three-valued (TRUE/FALSE/UNKNOWN, Kleene logic). The per-rule `onUnknown` setting defaults to NO_MATCH, and UNKNOWNs are reported in `ruleStats`. Out-of-order or duplicate record times fail the run. Numbers compare as exact BigDecimal values, and NaN or infinity gives UNKNOWN.
- Reproducibility: each run carries a `RunManifest` (engine version and commit, `strategyHash`, per-file and overall `dataHash`, closed and missing dates, assumptions). `compare` warns when runs aren't comparable; `strict=true` returns 409.

**Architecture (Maven multi-module):**
- `tradeevo-core`: plain Java with no Spring (a guard test enforces this), depending only on Jackson and slf4j. Packages: `core.rule`, `core.rule.types`, `core.strategy`, `core.data`, `core.engine`, `core.json`, `core.error`, `store`. `StrategyEngine.create(...)` works without Spring.
- `tradeevo-app`: Spring Boot. It holds the REST API (catalog, strategies, backtests and compare, one-shot real-time evaluation with data in the request body), a picocli CLI (`java -jar tradeevo-app/target/tradeevo.jar backtest ...`), Spring wiring (`CoreConfiguration`) and the strategy classes. The example strategy is `ThresholdSignalStrategy`.
- The full spec is at `docs/superpowers/specs/2026-10-10-tradeevo-core-design.md`, including its revision sections.

**Tech stack:**
- Java 25, JDK at `C:\Program Files\Java\jdk-25.0.4.1`. The default `java` on PATH is 17, so export `JAVA_HOME` before building.
- Spring Boot 3.5.15 is the parent. Resource filtering uses `@...@` delimiters, not `${}`.
- Build with the Maven Wrapper (`./mvnw`, Maven 3.9.11). The system Maven 3.6.0 is too old.
- Libraries: Jackson, picocli 4.7.7, git-commit-id-maven-plugin 9.0.1, JUnit 5 and AssertJ.

**Guidelines:** Keep instrument or market logic out of `tradeevo-core`. Make new rule types generic and parameterized. Add new strategies as classes in `tradeevo-app` with their own data format and declared assumptions. Preserve determinism and the manifest hashes.
