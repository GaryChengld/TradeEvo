# SPX 0DTE Option Price Simulator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a pricing library that strategy classes call to price SPX 0DTE options and credit spreads from SPX, VIX and the time of day, with every parameter in `config/pricing-models.json`.

**Architecture:** the pure, deterministic pricing code lives in the package `com.tradeevo.pricing` in `tradeevo-app`. `PricingModels` validates the parameters file and strategy overrides, and builds a `SpreadPricer` (Black-Scholes or empirical). One Spring bean, `SpreadPricers`, loads the file lazily for strategies.

**Tech Stack:** Java 25, Jackson, JUnit 5 and AssertJ. Build with `./mvnw` (export `JAVA_HOME="/c/Program Files/Java/jdk-25.0.4.1"`).

**Spec:** `docs/superpowers/specs/2026-10-18-spx-0dte-option-price-simulator-design.md`

## Global Constraints

- No numeric model parameter is hard-coded in Java. Every one comes from `config/pricing-models.json` or a strategy override.
- Mathematical constants (Hart's coefficients, VIX/100) are not parameters.
- The pricing code has no Spring imports, except the `SpreadPricers` bean method in `TradeEvoConfiguration`.
- `tradeevo-core` gets only one addition: an `InvalidConfigException(String summary, List<String> errors)` constructor. It gains no pricing logic.
- Accuracy: ±20% relative tolerance applies only to market-based references, through `PricingAssertions.assertWithinTolerance`. Identities and bounds keep tight tolerances.
- Tests are deterministic, with no randomness.
- **No commits.** `CLAUDE.md` forbids committing until the user asks, so this plan has no commit steps.

## File Structure

`tradeevo-app/src/main/java/com/tradeevo/pricing/`:

| File | Responsibility |
|---|---|
| `OptionType.java` | `CALL`, `PUT` |
| `SpreadDirection.java` | `BULL_PUT`, `BEAR_CALL`; `optionType()`, `distance(spx, shortStrike)`, `requireStrikeOrder(short, long)` |
| `ParamKind.java` | `FINITE`, `POSITIVE`, `POSITIVE_INTEGER`, `TIME_OF_DAY`: one source of value rules, for both JSON checks and records |
| `TimeParams.java`, `BlackScholesParams.java`, `EmpiricalParams.java` | validated parameter records |
| `ZeroDteTime.java` | `minutesLeft(now)` and `years(now)` |
| `ExpectedMove.java` | `points(spx, vix, years)` and `distance(...)` |
| `NormalDistribution.java` | `cdf(x)` with Hart/West |
| `BlackScholes.java` | European price, clamped to the no-arbitrage bounds |
| `ZeroDteOptionPricer.java` | single 0DTE option |
| `SpreadPricer.java` | interface |
| `BlackScholesSpreadPricer.java`, `EmpiricalSpreadPricer.java` | the two models |
| `Inputs.java` | call-time input checks shared by the pricers |
| `PricingModels.java` | parses and validates the file, validates and resolves the strategy's `pricing` block |
| `ResolvedPricer.java` | `(String model, SpreadPricer pricer, ObjectNode parameters)` |
| `SpreadPricers.java` | the bean: lazy load once, then `validate` and `create` |

Other files:
- **Create:**
  - `config/pricing-models.json`
  - tests under `tradeevo-app/src/test/java/com/tradeevo/pricing/`
- **Modify:**
  - `TradeEvoProperties`: add `pricingFile`.
  - `TradeEvoConfiguration`: add the bean.
  - `InvalidConfigException`: add the constructor.
  - Parent `pom.xml`: add the surefire property `tradeevo.config`.
  - `AppTestSupport`: add `PRICING_FILE`.
  - `README.md`: add a short pricing section.

---

### Task 1: Math primitives

**Files:** `OptionType`, `NormalDistribution`, `BlackScholes`. Test: `NormalDistributionTest`, `BlackScholesTest`, and `PricingAssertions` (a test helper).

**Produces:**
- `NormalDistribution.cdf(double): double`
- `BlackScholes.price(double spot, double strike, double sigma, double years, double rate, double dividend, OptionType type): double`
  - Returns intrinsic when `years ≤ 0`, and the discounted forward intrinsic when σ = 0.
  - Clamps to the bounds `[max(Se^{-qT} − Ke^{-rT}, 0), Se^{-qT}]` for a call, and the mirror for a put.
  - Throws `IllegalArgumentException` for non-positive or non-finite spot or strike, negative or non-finite σ, and non-finite years, rate or dividend.

- [ ] Write the tests:
  - Φ at 0, ±1, ±1.96 and ±5 against reference values (to 1e-14 absolute, and 1e-12 relative for Φ(−5)), plus symmetry.
  - Hull 4.76 and 0.81 (to 0.01).
  - Put-call parity (to 1e-10).
  - Intrinsic at `years ≤ 0` and at σ = 0.
  - The price rises with σ and with time.
  - Extreme inputs are finite.
  - The no-arbitrage bounds hold.
- [ ] Run `./mvnw -q -pl tradeevo-app test -Dtest='NormalDistributionTest,BlackScholesTest'`. Expected: they fail to compile.
- [ ] Implement.
- [ ] Rerun. Expected: they pass.

### Task 2: Parameters, time and expected move

**Files:** `ParamKind`, `TimeParams`, `BlackScholesParams`, `EmpiricalParams`, `ZeroDteTime`, `ExpectedMove`, `SpreadDirection`, `Inputs`. Test: `ZeroDteTimeTest`.

**Produces:**
- `TimeParams(LocalTime expiration, int tradingDaysPerYear, int sessionMinutes)`
- `BlackScholesParams(double ivMultiplier, double rate, double dividend)`
- `EmpiricalParams(base, decay, cScale, cPivot, cExponent, cMin, cMax, mHeight, mCenter, mWidth, aHeight, aCenter, aWidth)`, all `double`
- `ZeroDteTime(TimeParams)`, with `minutesLeft(LocalTime): double` and `years(LocalTime): double`
- `ExpectedMove.points(spx, vix, years)` and `ExpectedMove.distance(spx, shortStrike, direction, em)`
- `SpreadDirection.distance(spx, shortStrike)`, `optionType()` and `requireStrikeOrder(shortStrike, longStrike)`
- `ParamKind.problem(JsonNode): String` (null when valid), `problem(double)` and `require(String name, double value): double`

- [ ] Write the tests:
  - 16:00 gives 0; 15:59:30 gives 0.5; after 16:00 gives 0.
  - Expiration 13:00 at 12:00 gives 60.
  - Custom trading-days and session-minutes settings change `years`.
  - Records reject invalid values.
- [ ] Run. Expected: they fail. Implement. Run. Expected: they pass.

### Task 3: Option and spread pricers

**Files:** `ZeroDteOptionPricer`, `SpreadPricer`, `BlackScholesSpreadPricer`, `EmpiricalSpreadPricer`. Tests:
- `ZeroDteOptionPricerTest`: straddle within ±20% of $36; intrinsic at expiry; the multiplier.
- `EmpiricalSpreadPricerTest`: port of the original function; continuity; intrinsic at T ≤ 0; bounds; monotonic.
- `BlackScholesSpreadPricerTest`.
- `EdgeCaseTest`: everything in the spec's **Edge-case and property tests** section.

**Produces:**
- `SpreadPricer.price(double spx, double vix, LocalTime now, SpreadDirection direction, double shortStrike, double longStrike): double`
- `ZeroDteOptionPricer.price(double spx, double strike, double vix, LocalTime now, OptionType type): double`

- [ ] Write the tests.
- [ ] Run. Expected: they fail. Implement. Run. Expected: they pass.

### Task 4: Parameters file, overrides and bean

**Files:** `config/pricing-models.json`, `PricingModels`, `ResolvedPricer`, `SpreadPricers`, plus the property, bean, exception constructor, surefire property and `AppTestSupport` changes. Tests:
- `PricingModelsTest`: the shipped file loads; each error case, with paths; several errors at once.
- `SpreadPricersTest`: model selection and default; overrides and their errors; resolved parameters; lazy failure; cached after the first read.
- `ModelComparisonTest`: pinned prices from the shipped file at N ∈ {0, 0.5, 1, 1.28} at 12:00.

**Produces:**
- `PricingModels.load(Path, ObjectMapper)` and `parse(JsonNode)`. Both throw `InvalidConfigException`.
- `defaultModel()`, `timeParams()`, `blackScholesParams()`, `empiricalParams()`
- `validate(JsonNode pricing, String path): List<String>`
- `resolve(JsonNode pricing, String path): ResolvedPricer`
- `SpreadPricers(Path, ObjectMapper)`, with `models()`, `validate(JsonNode, String)` and `create(JsonNode)`. `create` resolves with the path `settings.pricing`.

- [ ] Write the tests.
- [ ] Run. Expected: they fail. Implement. Run. Expected: they pass.
- [ ] Run the full build, `./mvnw -q verify`. Expected: all tests pass, the existing 77 plus the new ones.
- [ ] Add the README section.
